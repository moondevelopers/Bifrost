package co.topl.attestation

import co.topl.utils.codecs.implicits._
import co.topl.attestation.AddressCodec.implicits._
import co.topl.utils.StringDataTypes.Latin1Data
import co.topl.utils.{KeyFileTestHelper, NodeGenerators}
import org.scalatest.matchers.should.Matchers
import org.scalatest.propspec.AnyPropSpec
import org.scalatestplus.scalacheck.{ScalaCheckDrivenPropertyChecks, ScalaCheckPropertyChecks}

class KeySpec
    extends AnyPropSpec
    with ScalaCheckPropertyChecks
    with ScalaCheckDrivenPropertyChecks
    with NodeGenerators
    with Matchers
    with KeyFileTestHelper {

  var password: Latin1Data = _
  var messageByte: Array[Byte] = _

  var address: Address = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    password = Latin1Data.unsafe(sampleUntilNonEmpty(stringGen))
    messageByte = sampleUntilNonEmpty(nonEmptyBytesGen)

    import org.scalatest.TryValues._

    address = keyRing.DiskOps.generateKeyFile(password).success.value
  }

  property("The randomly generated address from generateKeyFile should exist in keyRing") {
    keyRing.addresses.contains(address) shouldBe true
  }

  property("Once we lock the generated address, it will be removed from the secrets set in the keyRing") {
    keyRing.removeFromKeyring(address)

    /** There will be a warning for locking again if a key is already locked */
    keyRing.removeFromKeyring(address)

    keyRing.addresses.contains(address) shouldBe false
  }

  property("Once unlocked, the address will be accessible from the keyRing again") {
    keyRing.DiskOps.unlockKeyFile(address.encodeAsBase58, password)

    /** There will be a warning for unlocking again if a key is already unlocked */
    keyRing.DiskOps.unlockKeyFile(address.encodeAsBase58, password)

    keyRing.addresses.contains(address) shouldBe true
  }

  property("LookupPublickKey should return the correct public key to the address") {
    keyRing.lookupPublicKey(address).get.address shouldEqual address
  }

  property("The proof generated by signing the message Bytes with address should be valid") {
    val proof = keyRing.signWithAddress(address)(messageByte).get
    val prop = keyRing.lookupPublicKey(address).get

    proof.isValid(prop, messageByte) shouldBe true
  }

  property("Trying to sign a message with an address not on the keyRing will fail") {
    val randAddr: Address = addressGen.sample.get
    val error = intercept[Exception](keyRing.signWithAddress(randAddr)(messageByte))
    error.getMessage shouldEqual "Unable to find secret for the given address"
  }

  property("The proof from signing with an address should only be valid for the corresponding proposition") {
    val prop = keyRing.lookupPublicKey(address).get

    val newAddr: Address = keyRing.DiskOps.generateKeyFile(Latin1Data.unsafe(stringGen.sample.get)).get
    val newProp = keyRing.lookupPublicKey(newAddr).get
    val newProof = keyRing.signWithAddress(newAddr)(messageByte).get

    newProof.isValid(prop, messageByte) shouldBe false
    newProof.isValid(newProp, messageByte) shouldBe true
  }

  //TODO: Jing - test importPhrase
}
