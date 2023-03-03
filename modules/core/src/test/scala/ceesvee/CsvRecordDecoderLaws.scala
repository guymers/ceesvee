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

  case class A(v: Boolean)
  object A {
    implicit val decoder: CsvRecordDecoder[A] = CsvRecordDecoder.derived
  }

  case class B(v: Int)
  object B {
    implicit val decoder: CsvRecordDecoder[B] = CsvRecordDecoder.derived
  }

  case class C(v: String)
  object C {
    implicit val decoder: CsvRecordDecoder[C] = CsvRecordDecoder.derived
  }

  implicit val arbitraryA: Arbitrary[A] = Arbitrary { Arbitrary.arbBool.arbitrary.map(A(_)) }
  implicit val arbitraryFA: Arbitrary[CsvRecordDecoder[A]] = Arbitrary { Gen.const(A.decoder) }

  implicit val arbitraryB: Arbitrary[B] = Arbitrary { Arbitrary.arbInt.arbitrary.map(B(_)) }
  implicit val arbitraryFB: Arbitrary[CsvRecordDecoder[B]] = Arbitrary { Gen.const(B.decoder) }

  implicit val arbitraryC: Arbitrary[C] = Arbitrary { Arbitrary.arbString.arbitrary.map(C(_)) }
  implicit val arbitraryFC: Arbitrary[CsvRecordDecoder[C]] = Arbitrary { Gen.const(C.decoder) }

  // these instances are bullshit
  implicit val arbitraryFAB: Arbitrary[CsvRecordDecoder[A => B]] = Arbitrary {
    val decoder: CsvRecordDecoder[A => B] = new CsvRecordDecoder[A => B] {
      override val numFields = 0
      override def decode(fields: IndexedSeq[String]) = Right(a => B(if (a.v) 1 else 0))
    }
    Gen.const(decoder)
  }

  implicit val arbitraryFBC: Arbitrary[CsvRecordDecoder[B => C]] = Arbitrary {
    val decoder: CsvRecordDecoder[B => C] = new CsvRecordDecoder[B => C] {
      override val numFields = 0
      override def decode(fields: IndexedSeq[String]) = Right(b => C(b.v.toString))
    }
    Gen.const(decoder)
  }

  implicit val cogenA: Cogen[A] = Cogen.cogenBoolean.contramap(_.v)
  implicit val cogenB: Cogen[B] = Cogen.cogenInt.contramap(_.v)
  implicit val cogenC: Cogen[C] = Cogen.cogenString.contramap(_.v)

  implicit val eqA: Eq[CsvRecordDecoder[A]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("true")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }

  implicit val eqB: Eq[CsvRecordDecoder[B]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("42")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }

  implicit val eqC: Eq[CsvRecordDecoder[C]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("a string")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }

  implicit val eqABC: Eq[CsvRecordDecoder[(A, B, C)]] = Eq.instance { case (x, y) =>
    val v = IndexedSeq("true", "42", "a string")
    x.numFields == y.numFields && x.decode(v) == y.decode(v)
  }
}
