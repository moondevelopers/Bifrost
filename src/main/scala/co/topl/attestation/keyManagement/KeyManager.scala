package co.topl.attestation.keyManagement

import akka.actor._
import co.topl.attestation.keyManagement.KeyManager.ForgerView
import co.topl.attestation.{Address, AddressEncoder}
import co.topl.settings.{AppContext, AppSettings, ForgingSettings}
import co.topl.utils.Logging
import co.topl.utils.NetworkType._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class KeyManager(
  private val initialKeyRing:       KeyRing[PrivateKeyCurve25519, KeyfileCurve25519],
  private val initialRewardAddress: Option[Address]
)(implicit network:NetworkPrefix, settings: AppSettings, appContext: AppContext)
    extends Actor
    with Logging {

  import KeyManager.ReceivableMessages._

  override def receive: Receive = receive(initialKeyRing, initialRewardAddress)

  def receive(keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519], rewardAddress: Option[Address]): Receive = {
    case CreateKey(password)                        => sender() ! keyRing.DiskOps.generateKeyFile(password)
    case UnlockKey(addr, password)                  => sender() ! keyRing.DiskOps.unlockKeyFile(addr, password)
    case LockKey(addr)                              => sender() ! keyRing.removeFromKeyring(addr)
    case ImportKey(password, mnemonic, lang)        => sender() ! keyRing.importPhrase(password, mnemonic, lang)
    case ListKeys                                   => sender() ! keyRing.addresses
    case UpdateRewardsAddress(address)              => sender() ! updateRewardsAddress(keyRing, address)
    case GetRewardsAddress                          => sender() ! rewardAddress.fold("none")(_.toString)
    case GetForgerView                              => sender() ! ForgerView(keyRing.addresses, rewardAddress)
    case SignMessageWithAddress(address: Address, message: Array[Byte]) =>
      sender() ! signMessageWithAddress(address, message, keyRing)
    case GetPublicKeyFromAddress(address: Address)  => sender() ! getPublicKeyFromAddress(address, keyRing)
    case GenerateInititalAddresses                  => sender() ! generateInitialAddresses(keyRing, rewardAddress)
  }

  /** Updates the rewards address from the API */
  private def updateRewardsAddress(
    keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519],
    address: Address
  ): String = {
    val newRewardAddress = Some(address)
    context.become(receive(keyRing, newRewardAddress))
    newRewardAddress.fold("none")(_.toString)
  }

  /**
    * Signs a message using an address in the given key ring.
    * @param address the address to sign with
    * @param message the message to sign
    * @param keyRing contains the address secret to sign with
    * @return a try which results in a proof if successful
    */
  private def signMessageWithAddress(
    address: Address,
    message: Array[Byte],
    keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519]
  ) =
    keyRing.signWithAddress(address)(message)

  /** Gets a public key from a given address */
  private def getPublicKeyFromAddress(address: Address, keyRing: KeyRing[PrivateKeyCurve25519, KeyfileCurve25519]) =
    keyRing.lookupPublicKey(address)

  /**
    * Generates the initial addresses in the node for a private or local test network.
    * @param keyRing the key ring to generate addresses in
    * @param rewardAddress the current reward address
    * @return a try which results in a ForgerView of the current addresses and rewards address
    */
  private def generateInitialAddresses(
    keyRing:       KeyRing[PrivateKeyCurve25519, KeyfileCurve25519],
    rewardAddress: Option[Address]
  ): Try[ForgerView] =
    // If the keyring is not already populated and this is a private/local testnet, generate the keys
    // this is for when you have started up a private network and are attempting to resume it using
    // the same seed you used previously to continue forging
    if (keyRing.addresses.isEmpty && Seq(PrivateTestnet, LocalTestnet).contains(appContext.networkType)) {
      settings.forging.privateTestnet match {
        case Some(sfp) =>
          val (numAccts, seed) = (sfp.numTestnetAccts, sfp.genesisSeed)

          keyRing
            .generateNewKeyPairs(numAccts, seed)
            .map(keys => keys.map(_.publicImage.address))
            .map { addresses =>
              val newRewardAddress = if (rewardAddress.isEmpty) Some(addresses.head) else rewardAddress

              context.become(receive(keyRing, newRewardAddress))

              ForgerView(addresses, newRewardAddress)
            }
        case _ =>
          log.warn("No private testnet settings found!")
          Success(ForgerView(keyRing.addresses, rewardAddress))
      }
    } else {
      Success(ForgerView(keyRing.addresses, rewardAddress))
    }
}

object KeyManager extends Logging {

  val actorName = "keyManager"

  case class ForgerView(addresses: Set[Address], rewardAddr: Option[Address])

  object ReceivableMessages {
    case class UnlockKey(addr: String, password: String)

    case class LockKey(addr: Address)

    case object ListKeys

    case class CreateKey(password: String)

    case class ImportKey(password: String, mnemonic: String, lang: String)

    case object GetRewardsAddress

    case class UpdateRewardsAddress(address: Address)

    case object GetForgerView

    case class SignMessageWithAddress(address: Address, message: Array[Byte])

    case class GetPublicKeyFromAddress(address: Address)

    case object GenerateInititalAddresses
  }

}

object KeyManagerRef extends Logging {

  def props(settings: AppSettings, appContext: AppContext)(implicit ec: ExecutionContext, np: NetworkPrefix): Props =
    Props(
      new KeyManager(createKeyRing(settings), tryGetRewardsAddressFromSettings(settings.forging, appContext))(
        np,
        settings,
        appContext
      )
    )

  def apply(name: String, settings: AppSettings, appContext: AppContext)(implicit
    system:       ActorSystem,
    ec:           ExecutionContext
  ): ActorRef =
    system.actorOf(props(settings, appContext)(ec, appContext.networkType.netPrefix), name)

  /** Tries to get a configured rewards address from the forging settings. */
  private def tryGetRewardsAddressFromSettings(
    forgingSettings: ForgingSettings,
    appContext:      AppContext
  ): Option[Address] =
    forgingSettings.rewardsAddress.flatMap {
      AddressEncoder.fromStringWithCheck(_, appContext.networkType.netPrefix) match {
        case Failure(ex) =>
          log.warn(s"${Console.YELLOW}Unable to set rewards address due to $ex ${Console.RESET}")
          None

        case Success(addr) => Some(addr)
      }
    }

  /** Creates a new key ring. */
  private def createKeyRing(
    settings:    AppSettings
  )(implicit np: NetworkPrefix): KeyRing[PrivateKeyCurve25519, KeyfileCurve25519] = {
    val keyFileDir = settings.application.keyFileDir
      .ensuring(_.isDefined, "A keyfile directory must be specified")
      .get

    KeyRing[PrivateKeyCurve25519, KeyfileCurve25519](keyFileDir, KeyfileCurve25519)
  }
}
