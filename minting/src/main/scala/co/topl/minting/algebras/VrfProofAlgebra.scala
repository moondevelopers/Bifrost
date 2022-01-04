package co.topl.minting.algebras

import co.topl.models._

trait VrfProofAlgebra[F[_]] {
  def precomputeForEpoch(epoch: Epoch, previousEta: Eta): F[Unit]
  def ineligibleSlots(epoch:    Epoch, eta:         Eta): F[Vector[Slot]]
  def rhoForSlot(slot:          Slot, eta:          Eta): F[Rho]
  def proofForSlot(slot:        Slot, eta:          Eta): F[Proofs.Knowledge.VrfEd25519]
}
