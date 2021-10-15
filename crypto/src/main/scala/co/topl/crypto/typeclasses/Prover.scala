package co.topl.crypto.typeclasses

import co.topl.crypto.signing.{Ed25519, ExtendedEd25519, MessageToSign}
import co.topl.crypto.typeclasses.Signable.ops._
import co.topl.models.Proofs.Signature
import co.topl.models._

import scala.language.implicitConversions

trait Prover[T, Prf <: Proof] {

  /**
   * Creates a Proof for some signable Data using some value T
   * @param t a value which can construct a Proof, usually a SecretKey
   * @param data unsigned data that can be represented bytes to be signed
   * @return a Proof
   */
  def proveWith[Data: Signable](t: T, data: Data): Prf
}

object Prover {

  def apply[T, Prf <: Proof](implicit p: Prover[T, Prf]): Prover[T, Prf] = p

  trait Instances {
    implicit val ed25519Proves: Prover[SecretKeys.Ed25519, Proofs.Signature.Ed25519] =
      new Prover[SecretKeys.Ed25519, Proofs.Signature.Ed25519] {

        def proveWith[Data: Signable](t: SecretKeys.Ed25519, data: Data): Signature.Ed25519 =
          new Ed25519().sign(t, MessageToSign(data.signableBytes.toArray))
      }

    implicit val extendedEd25519Proves: Prover[SecretKeys.ExtendedEd25519, Proofs.Signature.Ed25519] =
      new Prover[SecretKeys.ExtendedEd25519, Proofs.Signature.Ed25519] {

        def proveWith[Data: Signable](t: SecretKeys.ExtendedEd25519, data: Data): Proofs.Signature.Ed25519 =
          new ExtendedEd25519().sign(t, MessageToSign(data.signableBytes.toArray))
      }
  }

  trait Implicits {

    implicit class TOps[T](private val t: T) {

      def prove[Prf <: Proof, Data: Signable](data: Data)(implicit proves: Prover[T, Prf]): Prf =
        proves.proveWith(t, data)
    }
  }

  object implicits extends Implicits

  object instances extends Instances
}
