package co.topl.modifier.block

import co.topl.crypto.{ FastCryptographicHash, PrivateKey25519, Signature25519 }
import co.topl.modifier.ModifierId
import co.topl.modifier.block.Block._
import co.topl.modifier.transaction.Transaction
import co.topl.nodeView.NodeViewModifier.ModifierTypeId
import co.topl.nodeView.state.box.ArbitBox
import co.topl.nodeView.state.box.serialization.BoxSerializer
import co.topl.nodeView.history.History
import co.topl.nodeView.{ BifrostNodeViewModifier, NodeViewModifier }
import co.topl.utils.serialization.BifrostSerializer
import io.circe.syntax._
import io.circe.{ Encoder, Json }
import scorex.crypto.encode.Base58
import supertagged.@@
// fixme: JAA 0 2020.07.19 - why is protobuf still used here?
import serializer.BloomTopics

import scala.collection.BitSet

/**
 * A block is an atomic piece of data network participates are agreed on.
 *
 * A block has:
 * - transactional data: a sequence of transactions, where a transaction is an atomic state update.
 * Some metadata is possible as well(transactions Merkle tree root, state Merkle tree root etc).
 *
 * - consensus data to check whether block was generated by a right party in a right way. E.g.
 * "baseTarget" & "generatorSignature" fields in the Nxt block structure, nonce & difficulty in the
 * Bitcoin block structure.
 *
 * - a signature(s) of a block generator(s)
 *
 * - additional data: block structure version no, timestamp etc
 */

case class Block ( parentId: BlockId,
                   timestamp: Timestamp,
                   forgerBox: ArbitBox,
                   signature: Signature25519,
                   txs: Seq[Transaction],
                   version  : Version
                 ) extends BifrostNodeViewModifier {

  type M = Block

  lazy val id: BlockId = ModifierId(serializedId)

  lazy val modifierTypeId: ModifierTypeId = Block.modifierTypeId

  lazy val transactions: Option[Seq[Transaction]] = Some(txs)

  lazy val serializer: BifrostSerializer[Block] = BlockSerializer

  lazy val messageToSign: Array[Byte] = {
    val noSigCopy = this.copy(signature = Signature25519(Array.empty))
    serializer.toBytes(noSigCopy)
  }

  lazy val serializedId: Array[Byte] = FastCryptographicHash(messageToSign)

  lazy val serializedParentId: Array[Byte] = parentId.hashBytes

//  lazy val json: Json = Map(
//    "id" -> Base58.encode(serializedId).asJson,
//    "parentId" -> Base58.encode(serializedParentId).asJson,
//    "timestamp" -> timestamp.asJson,
//    "generatorBox" -> Base58.encode(BoxSerializer.toBytes(forgerBox)).asJson,
//    "signature" -> Base58.encode(signature.signature).asJson,
//    "txs" -> txs.map(_.json).asJson,
//    "version" -> version.asJson,
//    "blockSize" -> serializer.toBytes(this).length.asJson
//    ).asJson

  lazy val json: Json = Block.jsonEncoder(this)
}

object Block {

  type BlockId = ModifierId
  type Timestamp = Long
  type Version = Byte

  val blockIdLength: Int = NodeViewModifier.ModifierIdSize
  val modifierTypeId: Byte @@ NodeViewModifier.ModifierTypeId.Tag = ModifierTypeId @@ (3: Byte)
  val signatureLength: Int = Signature25519.SignatureSize

  def create ( parentId  : BlockId,
               timestamp : Timestamp,
               txs       : Seq[Transaction],
               box       : ArbitBox,
               //attachment: Array[Byte],
               privateKey: PrivateKey25519,
               version   : Version
             ): Block = {

    assert(box.proposition == privateKey.publicImage)

    // generate block message (block with empty signature) to be signed
    val block = Block(parentId, timestamp, box, Signature25519(Array.empty), txs, version)

    // generate signature from the block message and private key
    val signature =
      if (parentId == History.GenesisParentId) Signature25519(Array.empty) // genesis block will skip signature check
      else privateKey.sign(block.messageToSign)

    // return a valid block with the signature attached
    block.copy(signature = signature)
  }

  def createBloom ( txs: Seq[Transaction] ): Array[Byte] = {
    val bloomBitSet = txs.foldLeft(BitSet.empty)(
      ( total, b ) =>
        b.bloomTopics match {
          case Some(e) => total ++ Bloom.calcBloom(e.head, e.tail)
          case None    => total
        }
      ).toSeq
    BloomTopics(bloomBitSet).toByteArray
  }

  val jsonEncoder: Encoder[Block] = { b: Block ⇒
    Map(
      "id" -> Base58.encode(b.serializedId).asJson,
      "parentId" -> Base58.encode(b.serializedParentId).asJson,
      "timestamp" -> b.timestamp.asJson,
      "generatorBox" -> Base58.encode(BoxSerializer.toBytes(b.forgerBox)).asJson,
      "signature" -> Base58.encode(b.signature.signature).asJson,
      "txs" -> b.txs.map(_.json).asJson,
      "version" -> b.version.asJson,
      "blockSize" -> b.serializer.toBytes(b).length.asJson
      ).asJson
  }
}
