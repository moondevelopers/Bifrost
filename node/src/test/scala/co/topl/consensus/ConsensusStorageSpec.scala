package co.topl.consensus

import co.topl.crypto.Implicits._
import co.topl.utils.{blake2b256, CoreGenerators}
import com.google.common.primitives.Longs
import io.iohk.iodb.{ByteArrayWrapper, Store}
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

class ConsensusStorageSpec
    extends AnyFlatSpec
    with ScalaCheckPropertyChecks
    with Matchers
    with CoreGenerators
    with MockFactory {

  "totalStake" should "return default total stake after no updates with empty storage" in {
    forAll(positiveMediumIntGen) { defaultTotalStake =>
      val storage = new ConsensusStorage(None, defaultTotalStake)

      storage.totalStake shouldBe defaultTotalStake
    }
  }

  "totalStake" should "load total stake from storage on start with an LSM Store" in {
    forAll(positiveInt128Gen) { (storageTotalStake) =>
      val store = mock[Store]
      (store
        .get(_: ByteArrayWrapper))
        .expects(*)
        .onCall { key: ByteArrayWrapper =>
          if (key == ByteArrayWrapper(blake2b256("totalStake")))
            Some(ByteArrayWrapper(storageTotalStake.toByteArray))
          else Some(ByteArrayWrapper(Longs.toByteArray(0)))
        }
        .anyNumberOfTimes()

      val storage = new ConsensusStorage(Some(store), 100000)

      storage.totalStake shouldBe storageTotalStake
    }
  }

  "totalStake" should "return default total stake when storage does not contain value" in {
    forAll(positiveMediumIntGen) { defaultTotalStake =>
      val store = mock[Store]
      (store
        .get(_: ByteArrayWrapper))
        .expects(*)
        .returns(None)
        .anyNumberOfTimes()

      val storage = new ConsensusStorage(None, defaultTotalStake)

      storage.totalStake shouldBe defaultTotalStake
    }
  }

}
