package co.topl.modifier.transaction.builder

import cats.data.{Chain, NonEmptyChain}
import cats.implicits._
import co.topl.attestation.implicits._
import co.topl.models.{DionAddress, ModelGenerators, Transaction}
import co.topl.modifier.box._
import co.topl.utils.CommonGenerators
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{EitherValues, OptionValues}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class BuildUnprovenTransferSpec
    extends AnyFlatSpec
    with CommonGenerators
    with Matchers
    with EitherValues
    with OptionValues
    with ScalaCheckDrivenPropertyChecks {
  behavior of "TransferBuilder.buildUnprovenTransfer"

  val addressesGen: Gen[NonEmptyChain[DionAddress]] =
    Gen
      .zip(ModelGenerators.arbitraryDionAddress.arbitrary, Gen.listOf(ModelGenerators.arbitraryDionAddress.arbitrary))
      .map(gens => NonEmptyChain.one(gens._1).appendChain(Chain.fromSeq(gens._2)))

  it should "use all available poly boxes when making a transfer containing poly outputs" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      polyBoxGen
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox) =>
      val polyOutputs = toAddresses.map(address => Transaction.PolyOutput(address, sendAmount))

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress -> Seq(
            polyBox.copy(value = SimpleValue(polyOutputsAmount(polyOutputs) + fee.data))
          )
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          polyOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.value.inputs.map(_._2) should contain(polyBox.nonce)
    }
  }

  it should "not use any available arbit boxes when only polys are sent" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      polyBoxGen,
      arbitBoxGen
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox, arbitBox) =>
      val polyOutputs = toAddresses.map(address => Transaction.PolyOutput(address, sendAmount))

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress ->
          Seq(polyBox.copy(value = SimpleValue(polyOutputsAmount(polyOutputs) + fee.data)), arbitBox)
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          polyOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.value.inputs.map(_._2) should not contain arbitBox.nonce
    }
  }

  it should "not use any available asset boxes when only polys are sent" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      polyBoxGen,
      assetBoxGen
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox, assetBox) =>
      val polyOutputs = toAddresses.map(address => Transaction.PolyOutput(address, sendAmount))

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress ->
          Seq(polyBox.copy(value = SimpleValue(polyOutputsAmount(polyOutputs) + fee.data)), assetBox)
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          polyOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.value.inputs.map(_._2) should not contain assetBox.nonce
    }
  }

  it should "use all available poly boxes when only sending arbits" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      polyBoxGen,
      arbitBoxGen
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox, arbitBox) =>
      val arbitOutputs = toAddresses.map(address => Transaction.ArbitOutput(address, sendAmount))

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress ->
          Seq(
            polyBox.copy(value = SimpleValue(fee.data)),
            arbitBox.copy(value = SimpleValue(arbitOutputsAmount(arbitOutputs)))
          )
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          arbitOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.value.inputs.map(_._2) should contain(polyBox.nonce)
    }
  }

  it should "have a change output when more polys are supplied than needed" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      polyBoxGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox, changeAmount) =>
      val polyOutputs = toAddresses.map(address => Transaction.PolyOutput(address, sendAmount))

      val existingPolys = polyOutputsAmount(polyOutputs) + changeAmount.data + fee.data

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress -> Seq(polyBox.copy(value = SimpleValue(existingPolys)))
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          polyOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.value.feeOutput.value.value shouldBe changeAmount
    }
  }

  it should "have a change output when more arbits are supplied than needed" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      Gen.zip(polyBoxGen, arbitBoxGen),
      ModelGenerators.arbitraryPositiveInt128.arbitrary
    ) { (fromAddresses, toAddresses, fee, sendAmount, boxes, changeAmount) =>
      val arbitOutputs = toAddresses.map(address => Transaction.ArbitOutput(address, sendAmount))

      val existingArbits = arbitOutputsAmount(arbitOutputs) + changeAmount.data

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress ->
          Seq(boxes._2.copy(value = SimpleValue(existingArbits)), boxes._1.copy(value = SimpleValue(fee.data)))
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          arbitOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.value.coinOutputs.toList should contain(Transaction.ArbitOutput(fromAddresses.head, changeAmount))
    }
  }

  it should "be invalid if not enough polys are provided to pay the fee and payment" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary.filter(_.data > 0),
      ModelGenerators.arbitraryPositiveInt128.arbitrary.filter(_.data > 0),
      polyBoxGen
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox) =>
      val polyOutputs = toAddresses.map(address => Transaction.PolyOutput(address, sendAmount))

      val totalPolysSent = polyOutputsAmount(polyOutputs)

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress ->
          Seq(polyBox.copy(value = SimpleValue(totalPolysSent)))
        )

      val expectedFailure =
        BuildTransferFailures.InsufficientPolyFunds(
          totalPolysSent,
          totalPolysSent + fee.data
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          polyOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.left.value shouldBe expectedFailure
    }
  }

  it should "be invalid if not enough arbits are provided to pay the outputs" in {
    forAll(
      addressesGen,
      addressesGen,
      ModelGenerators.arbitraryPositiveInt128.arbitrary,
      ModelGenerators.arbitraryPositiveInt128.arbitrary.filter(_.data > 0),
      polyBoxGen,
      arbitBoxGen
    ) { (fromAddresses, toAddresses, fee, sendAmount, polyBox, arbitBox) =>
      val arbitOutputs = toAddresses.map(address => Transaction.ArbitOutput(address, sendAmount))

      val totalArbitsSent = arbitOutputsAmount(arbitOutputs)

      val existingArbits = totalArbitsSent - 1

      val boxReader =
        MockBoxReader.fromSeq(
          fromAddresses.head.toAddress ->
          Seq(arbitBox.copy(value = SimpleValue(existingArbits)), polyBox)
        )

      val expectedFailure =
        BuildTransferFailures.InsufficientArbitFunds(
          existingArbits,
          totalArbitsSent
        )

      val request =
        TransferRequests.UnprovenTransferRequest(
          fromAddresses.toList,
          arbitOutputs.toList,
          fromAddresses.head,
          fromAddresses.head,
          fee,
          None,
          minting = false
        )

      val transferResult = TransferBuilder.buildUnprovenTransfer(boxReader, request, BoxSelectionAlgorithms.All)

      transferResult.left.value shouldBe expectedFailure
    }
  }
}
