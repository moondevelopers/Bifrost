package co.topl.attestation

import cats.implicits._
import co.topl.attestation.serialization.ProofSerializer
import co.topl.crypto.signatures.{Curve25519, PublicKey, Signature}
import co.topl.keyManagement.{PrivateKeyCurve25519, Secret}
import co.topl.utils.AsBytes.implicits._
import co.topl.utils.StringTypes.implicits._
import co.topl.utils.StringTypes.{Base58String, InvalidCharacterSet, StringValidationError}
import co.topl.utils.encode.{Base58, DecodingError, InvalidCharactersError, InvalidDataLengthError}
import co.topl.utils.serialization.{BifrostSerializer, BytesSerializable}
import com.google.common.primitives.Ints
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

import scala.util.Try

/**
 * The most general abstraction of fact a prover can provide a non-interactive proof
 * to open a box or to modify an account
 *
 * A proof is non-interactive and thus serializable
 */
sealed trait Proof[P <: Proposition] extends BytesSerializable {

  def isValid(proposition: P, message: Array[Byte]): Boolean

  override type M = Proof[_]

  override def serializer: BifrostSerializer[Proof[_]] = ProofSerializer

  override def toString: String = Base58.encode(bytes).map(_.show).getOrElse("")

  override def equals(obj: Any): Boolean = obj match {
    case pr: Proof[_] => pr.bytes sameElements bytes
    case _            => false
  }

  override def hashCode(): Int = Ints.fromByteArray(bytes)

}

object Proof {

  def fromString(str: Base58String): Either[ProofFromStringError, Proof[_]] =
    for {
      bytes  <- Base58.decode(str).leftMap(Base58DecodingError)
      result <- ProofSerializer.parseBytes(bytes).toEither.leftMap(ProofParseFailure)
    } yield result

  sealed abstract class ProofFromStringError
  case class Base58ValidationError(error: StringValidationError) extends ProofFromStringError
  case class Base58DecodingError(error: DecodingError) extends ProofFromStringError
  case class ProofParseFailure(error: Throwable) extends ProofFromStringError

  implicit def jsonEncoder[PR <: Proof[_]]: Encoder[PR] = (proof: PR) => proof.toString.asJson

  implicit def jsonDecoder: Decoder[Proof[_]] =
    Decoder[Base58String].map(fromString(_).getOrElse(throw new Exception("Failed to parse value into proof")))
}

/** The proof for a given type of `Secret` and `KnowledgeProposition` */
sealed trait ProofOfKnowledge[S <: Secret, P <: KnowledgeProposition[S]] extends Proof[P]

/* ----------------- */ /* ----------------- */ /* ----------------- */ /* ----------------- */ /* ----------------- */ /* ----------------- */

/**
 * A proof corresponding to a PublicKeyCurve25519 proposition. This is a zero-knowledge proof that argues knowledge of
 * the underlying private key associated with a public key
 *
 * @param sig 25519 signature
 */
case class SignatureCurve25519(private[attestation] val sig: Signature)
    extends ProofOfKnowledge[PrivateKeyCurve25519, PublicKeyPropositionCurve25519] {

  private val signatureLength = sig.asBytes.length

  require(
    signatureLength == 0 || signatureLength == Curve25519.SignatureLength,
    s"$signatureLength != ${Curve25519.SignatureLength}"
  )

  def isValid(proposition: PublicKeyPropositionCurve25519, message: Array[Byte]): Boolean =
    Curve25519.verify(sig, message, PublicKey(proposition.pubKeyBytes.value))
}

object SignatureCurve25519 {
  lazy val signatureSize: Int = Curve25519.SignatureLength

  /** Helper function to create empty signatures */
  lazy val empty: SignatureCurve25519 = SignatureCurve25519(Signature(Array.emptyByteArray))

  /** Returns a signature filled with 1's for use in genesis signatures */
  lazy val genesis: SignatureCurve25519 =
    SignatureCurve25519(Signature(Array.fill(SignatureCurve25519.signatureSize)(1: Byte)))

  def apply(str: Base58String)(implicit d: DummyImplicit): SignatureCurve25519 =
    Proof.fromString(str) match {
      case Right(sig: SignatureCurve25519) => sig
      case Right(_)                        => throw new Exception("Parsed to incorrect signature type")
      case Left(Proof.Base58ValidationError(InvalidCharacterSet())) | Left(
            Proof.Base58DecodingError(InvalidCharactersError())
          ) =>
        throw new Exception("Invalid character in signature proof.")
      case Left(Proof.Base58DecodingError(InvalidDataLengthError())) =>
        throw new Exception("Invalid length of signature proof.")
      case Left(Proof.ProofParseFailure(error)) => throw new Exception(s"Error while parsing proof: $error")
      case Left(error)                          => throw new Exception(s"Invalid signature: $error")
    }

  // see circe documentation for custom encoder / decoders
  // https://circe.github.io/circe/codecs/custom-codecs.html
  implicit val jsonEncoder: Encoder[SignatureCurve25519] = (sig: SignatureCurve25519) => sig.toString.asJson
  implicit val jsonKeyEncoder: KeyEncoder[SignatureCurve25519] = (sig: SignatureCurve25519) => sig.toString
  implicit val jsonDecoder: Decoder[SignatureCurve25519] = Decoder[Base58String].map(apply)
  implicit val jsonKeyDecoder: KeyDecoder[SignatureCurve25519] = KeyDecoder[Base58String].map(apply)
}

/* ----------------- */ /* ----------------- */ /* ----------------- */ /* ----------------- */ /* ----------------- */ /* ----------------- */

case class ThresholdSignatureCurve25519(private[attestation] val signatures: Set[SignatureCurve25519])
    extends ProofOfKnowledge[PrivateKeyCurve25519, ThresholdPropositionCurve25519] {

  signatures.foreach { sig =>
    require(sig.sig.asBytes.length == SignatureCurve25519.signatureSize)
  }

  override def isValid(proposition: ThresholdPropositionCurve25519, message: Array[Byte]): Boolean = Try {
    // check that we have at least m signatures
    // JAA - the Try wraps this to expression so this check may prematurely exit evaluation and return false
    //       (i.e. the check should fail quickly)
    require(proposition.pubKeyProps.size >= proposition.threshold)

    // only need to check until the threshold is exceeded
    val numValidSigs = signatures.foldLeft(0) { (acc, sig) =>
      if (acc < proposition.threshold) {
        if (
          proposition.pubKeyProps
            .exists(prop => Curve25519.verify(sig.sig, message, PublicKey(prop.pubKeyBytes.asBytes)))
        ) {
          1
        } else {
          0
        }
      } else {
        0
      }
    }

    require(numValidSigs >= proposition.threshold)

  }.isSuccess

}

object ThresholdSignatureCurve25519 {

  def apply(str: Base58String): ThresholdSignatureCurve25519 =
    Proof.fromString(str) match {
      case Right(sig: ThresholdSignatureCurve25519) => sig
      case Right(_)                                 => throw new Exception("Parsed to incorrect signature type")
      case Left(error)                              => throw new Exception(s"Invalid signature: $error")
    }

  /** Helper function to create empty signatures */
  def empty(): ThresholdSignatureCurve25519 = ThresholdSignatureCurve25519(Set[SignatureCurve25519]())

  // see circe documentation for custom encoder / decoders
  // https://circe.github.io/circe/codecs/custom-codecs.html
  implicit val jsonEncoder: Encoder[ThresholdSignatureCurve25519] = (sig: ThresholdSignatureCurve25519) =>
    sig.toString.asJson

  implicit val jsonKeyEncoder: KeyEncoder[ThresholdSignatureCurve25519] = (sig: ThresholdSignatureCurve25519) =>
    sig.toString
  implicit val jsonDecoder: Decoder[ThresholdSignatureCurve25519] = Decoder[Base58String].map(apply)
  implicit val jsonKeyDecoder: KeyDecoder[ThresholdSignatureCurve25519] = KeyDecoder[Base58String].map(apply)
}
