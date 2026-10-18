package ceesvee

import cats.Eq
import cats.laws.discipline.ApplyTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

class CsvRecordDecoderLaws extends DisciplineSuite {
  import CsvRecordDecoderLaws.*

  checkAll(
    "Apply[CsvRecordDecoder]",
    ApplyTests[CsvRecordDecoder].apply[A, B, C],
  )
}

object CsvRecordDecoderLaws {

  case class A(v: Boolean) derives CsvRecordDecoder
  case class B(v: Int) derives CsvRecordDecoder
  case class C(v: String) derives CsvRecordDecoder

  given arbitraryA: Arbitrary[A] = Arbitrary { Arbitrary.arbBool.arbitrary.map(A(_)) }
  given arbitraryFA: Arbitrary[CsvRecordDecoder[A]] = Arbitrary { Gen.const(CsvRecordDecoder[A]) }

  given arbitraryB: Arbitrary[B] = Arbitrary { Arbitrary.arbInt.arbitrary.map(B(_)) }
  given arbitraryFB: Arbitrary[CsvRecordDecoder[B]] = Arbitrary { Gen.const(CsvRecordDecoder[B]) }

  given arbitraryC: Arbitrary[C] = Arbitrary { Arbitrary.arbString.arbitrary.map(C(_)) }
  given arbitraryFC: Arbitrary[CsvRecordDecoder[C]] = Arbitrary { Gen.const(CsvRecordDecoder[C]) }

  // these instances are bullshit
  given arbitraryFAB: Arbitrary[CsvRecordDecoder[A => B]] = Arbitrary {
    val decoder: CsvRecordDecoder[A => B] = new CsvRecordDecoder[A => B] {
      override val numFields = 0
      override def decode(fields: IndexedSeq[String]) = Right(a => B(if (a.v) 1 else 0))
    }
    Gen.const(decoder)
  }

  given arbitraryFBC: Arbitrary[CsvRecordDecoder[B => C]] = Arbitrary {
    val decoder: CsvRecordDecoder[B => C] = new CsvRecordDecoder[B => C] {
      override val numFields = 0
      override def decode(fields: IndexedSeq[String]) = Right(b => C(b.v.toString))
    }
    Gen.const(decoder)
  }

  given cogenA: Cogen[A] = Cogen.cogenBoolean.contramap(_.v)
  given cogenB: Cogen[B] = Cogen.cogenInt.contramap(_.v)
  given cogenC: Cogen[C] = Cogen.cogenString.contramap(_.v)

  given eqA: Eq[CsvRecordDecoder[A]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("true")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }

  given eqB: Eq[CsvRecordDecoder[B]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("42")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }

  given eqC: Eq[CsvRecordDecoder[C]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("a string")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }

  given eqABC: Eq[CsvRecordDecoder[(A, B, C)]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("true", "42", "a string")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }
}
