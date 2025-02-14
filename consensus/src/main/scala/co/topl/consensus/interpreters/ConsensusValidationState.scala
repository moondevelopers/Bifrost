package co.topl.consensus.interpreters

import cats.data.OptionT
import cats.implicits._
import cats.{Applicative, MonadThrow}
import co.topl.algebras.ClockAlgebra.implicits._
import co.topl.algebras._
import co.topl.consensus.algebras.ConsensusValidationStateAlgebra
import co.topl.consensus.models.BlockId
import co.topl.consensus.models.SignatureKesProduct
import co.topl.consensus.models.StakingAddress
import co.topl.eventtree.EventSourcedState
import co.topl.models._
import co.topl.models.utility.Ratio

object ConsensusValidationState {

  /**
   * Constructs a ConsensusValidationState using the combination of an EpochBoundary EventSourcedState and a ConsensusState
   * EventSourcecdState.  Requests for blocks at the tip of the chain will return data from 2 epochs before that block.
   */
  def make[F[_]: MonadThrow](
    genesisBlockId: BlockId,
    epochBoundaryEventSourcedState: EventSourcedState[F, EpochBoundariesEventSourcedState.EpochBoundaries[
      F
    ], BlockId],
    consensusDataEventSourcedState: EventSourcedState[F, ConsensusDataEventSourcedState.ConsensusData[
      F
    ], BlockId],
    clock: ClockAlgebra[F]
  ): F[ConsensusValidationStateAlgebra[F]] =
    Applicative[F].pure {
      new ConsensusValidationStateAlgebra[F] {
        def operatorRelativeStake(currentBlockId: BlockId, slot: Slot)(address: StakingAddress): F[Option[Ratio]] =
          useStateAtTargetBoundary(currentBlockId, slot)(consensusData =>
            OptionT(consensusData.operatorStakes.get(address))
              .semiflatMap(operatorStake =>
                consensusData.totalActiveStake
                  .getOrRaise(())
                  .map(totalActiveStake => Ratio(operatorStake, totalActiveStake))
              )
              .value
          )

        def operatorRegistration(currentBlockId: BlockId, slot: Slot)(
          address: StakingAddress
        ): F[Option[SignatureKesProduct]] =
          useStateAtTargetBoundary(currentBlockId, slot)(_.registrations.get(address))

        /**
         * Determines the N-2 epoch from the given block, then determines the final block ID of the N-2 epoch.  That
         * N-2 block is used in determining the `ConsensusState` to retrieve.  Once retrieved, it is applied to the
         * given `f` function
         */
        private def useStateAtTargetBoundary[Res](
          currentBlockId: BlockId,
          slot:           Slot
        )(f: ConsensusDataEventSourcedState.ConsensusData[F] => F[Res]): F[Res] =
          for {
            epoch <- clock.epochOf(slot)
            targetEpoch = epoch - 2
            // Note: Blocks created within the first two epochs should use the state from the genesis block
            boundaryBlockId <-
              if (targetEpoch >= 0)
                epochBoundaryEventSourcedState.useStateAt(currentBlockId)(_.getOrRaise(targetEpoch))
              else
                genesisBlockId.pure[F]
            res <- consensusDataEventSourcedState.useStateAt(boundaryBlockId)(f)
          } yield res
      }
    }
}
