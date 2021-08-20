package co.topl.fullnode

import cats.data.OptionT
import co.topl.consensus.crypto.Ratio
import co.topl.consensus.{ChainSelectionChain, LeaderElection}
import co.topl.minting.BlockMint
import co.topl.minting.Mint.ops._
import co.topl.models._
import co.topl.typeclasses.BlockGenesis
import co.topl.typeclasses.Identifiable.Instances._
import co.topl.typeclasses.Identifiable.ops._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object FullNode extends App {

  val slotTime = 10.millis
  val SlotsPerEpoch = 5000

  val leaderElectionConfig = LeaderElection
    .Config(lddCutoff = 0, precision = 16, baselineDifficulty = Ratio(1, 15), amplitude = Ratio(2, 5))

  val genesisBlock =
    BlockGenesis(Nil).create()

  implicit val mint: BlockMint = {
    def elect(parent: BlockHeaderV2) = {
      var hit: LeaderElection.Hit = None.orNull
      var slot = parent.slot + 1
      val key = LeaderElection.Key(
        TypedBytes(Bytes(Array.fill[Byte](33)(1))),
        TypedBytes(Bytes(Array.fill[Byte](33)(1)))
      )
      val startTime = System.currentTimeMillis()
      // TODO: Pre-compute for the current epoch
      while (hit == null) {
        LeaderElection
          .getHit(
            key,
            relativeStake = Ratio(1, 10),
            slot = slot,
            slotDiff = slot - parent.slot,
            epochNonce = chainSelectionState.epochNonce,
            leaderElectionConfig
          )
          .foreach(hit = _)
        slot += 1
      }
      val slotDiff = slot - parent.slot
      Thread.sleep(((slotDiff.toLong * slotTime.toMillis) - (System.currentTimeMillis() - startTime)).max(0L))
      BlockMint.Election(
        slot = slot,
        vrfCertificate = Bytes(Array.fill(32)(1)), // TODO: Serialize res.cert
        kesCertificate = Bytes(Array.fill(32)(1))
      )
    }
    new BlockMint(
      getCurrentTime = () => System.currentTimeMillis(),
      nextTransactions = _ => Future.successful(Nil),
      elect = parent => Future(elect(parent))
    )
  }

  implicit val bigIntHasLength: HasLength[BigInt] = _.bitLength

  private val chainSelectionState = new ChainSelectionState(genesisBlock)

  val chainSelectionChainImpl: ChainSelectionChain[Future, Throwable] =
    ChainSelectionChain[Future, Throwable](
      latestBlockId = genesisBlock.headerV2.id,
      firstBlockId = genesisBlock.headerV2.id,
      nextBlockId = None,
      currentBlock = chainSelectionState.currentBlock,
      getBlock = id => Future.successful(chainSelectionState.headersMap(id)),
      childIdOf = parentId => OptionT.fromOption(chainSelectionState.headerChildMap.get(parentId)),
      totalStake = () => Sized.max(chainSelectionState.stakeMap.values.map(_.data).sum, Lengths.`128`).toOption.get,
      stakeFor = address => chainSelectionState.stakeMap.get(address),
      epochNonce = () => chainSelectionState.epochNonce,
      append = header => chainSelectionState.append(header),
      removeLatest = () => chainSelectionState.removeLatest()
    )

  val blockChainIterator =
    Iterator.iterate(chainSelectionChainImpl) { impl =>
      val BlockV2(newHeader, newBody) =
        Await.result(
          BlockV2(impl.currentBlock, chainSelectionState.currentBlockBody).nextValue,
          2.minutes
        )
      chainSelectionState.currentBlockBody = newBody
      Await.result(
        impl
          .appendToLatest(newHeader)
          .valueOrF(e => Future.failed(new Exception(e.toString))),
        2.seconds
      )
    }

  blockChainIterator
    .takeWhile(_.currentBlock.slot <= SlotsPerEpoch)
    .foreach { impl =>
      println(
        s"Applied headerId=${new String(impl.currentBlock.id.dataBytes.toArray)}" +
        s" to parentHeaderId=${new String(impl.currentBlock.parentHeaderId.dataBytes.toArray)}" +
        s" at height=${impl.currentBlock.height}" +
        s" at slot=${impl.currentBlock.slot}" +
        s" at timestamp=${impl.currentBlock.timestamp}"
      )
    }

  println("Completed epoch")

}

class ChainSelectionState(genesisBlock: BlockV2) {

  var headersMap: Map[TypedIdentifier, BlockHeaderV2] = Map.empty
  var headerChildMap: Map[TypedIdentifier, TypedIdentifier] = Map.empty
  var stakeMap: Map[Address, Int128] = Map.empty
  var epochNonce: Nonce = Bytes(Array(1))
  var currentBlock = genesisBlock.headerV2
  var currentBlockBody = genesisBlock.blockBodyV2

  val append = (header: BlockHeaderV2) => {
    headersMap += (header.id           -> header)
    headerChildMap += (currentBlock.id -> header.id)
    currentBlock = header
  }

  val removeLatest = () => {
    headersMap -= currentBlock.id
    headerChildMap -= currentBlock.parentHeaderId
    currentBlock = headersMap(currentBlock.parentHeaderId)
  }
}
