package co.topl.modifier.box

import cats.implicits._
import co.topl.attestation.Address
import co.topl.modifier.box.AssetCode.AssetCodeVersion
import co.topl.utils.AsBytes.implicits._
import co.topl.utils.Extensions.StringOps
import co.topl.utils.StringTypes.Base58String
import co.topl.utils.StringTypes.implicits._
import co.topl.utils.encode.Base58
import co.topl.utils.serialization.{BifrostSerializer, BytesSerializable, Reader, Writer}
import com.google.common.primitives.Ints
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

import java.nio.charset.StandardCharsets

/**
 * AssetCode serves as a unique identifier for user issued assets
 */
case class AssetCode(version: AssetCodeVersion, issuer: Address, shortName: String) extends BytesSerializable {

  require(version == 1.toByte, "AssetCode version required to be 1")

  require(
    shortName.getValidLatin1Bytes
      .getOrElse(throw new Exception("String is not valid Latin-1"))
      .length <= AssetCode.shortNameLimit,
    "Asset short names must be less than 8 Latin-1 encoded characters"
  )

  override type M = AssetCode
  override def serializer: BifrostSerializer[AssetCode] = AssetCode

  override def toString: String = Base58.encode(bytes).map(_.show).getOrElse("")

  override def equals(obj: Any): Boolean = obj match {
    case ec: AssetCode => bytes sameElements ec.bytes
    case _             => false
  }

  override def hashCode(): Int = Ints.fromByteArray(bytes)
}

object AssetCode extends BifrostSerializer[AssetCode] {
  type AssetCodeVersion = Byte

  val shortNameLimit = 8 // limit to the asset shortName is 8 Latin-1 encoded characters

  implicit val jsonEncoder: Encoder[AssetCode] = (ac: AssetCode) => ac.toString.asJson
  implicit val jsonKeyEncoder: KeyEncoder[AssetCode] = (ac: AssetCode) => ac.toString

  implicit val jsonDecoder: Decoder[AssetCode] =
    Decoder.decodeString
      .emap(Base58String.validated(_).leftMap(_ => "Value is not Base 58"))
      .map(apply)
  implicit val jsonKeyDecoder: KeyDecoder[AssetCode] = KeyDecoder[Base58String].map(apply)

  private def apply(str: Base58String): AssetCode =
    Base58.decode(str).flatMap(parseBytes(_).toEither) match {
      case Right(ec) => ec
      case Left(_)   => throw new Exception("Failed to parse value to asset code.")
    }

  override def serialize(obj: AssetCode, w: Writer): Unit = {
    // should be safe to assume Latin-1 encoding since AssetCode already checks this once instantiation
    val paddedShortName = obj.shortName.getBytes(StandardCharsets.ISO_8859_1).padTo(shortNameLimit, 0: Byte)

    w.put(obj.version)
    Address.serialize(obj.issuer, w)
    w.putBytes(paddedShortName)
  }

  override def parse(r: Reader): AssetCode = {
    val version = r.getByte()
    val issuer = Address.parse(r)
    val shortNameBytes = r.getBytes(shortNameLimit).filter(_ != 0)
    val shortName = new String(shortNameBytes, StandardCharsets.ISO_8859_1)

    new AssetCode(version, issuer, shortName)
  }
}
