package ceesvee

import cats.Eq
import cats.laws.discipline.ContravariantSemigroupalTests
import munit.DisciplineSuite
import org.scalacheck.Arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

class CsvRecordEncoderLaws extends DisciplineSuite {
  import CsvRecordEncoderLaws.*

  checkAll(
    "ContravariantSemigroupalTests[CsvRecordEncoder]",
    ContravariantSemigroupalTests[CsvRecordEncoder].contravariantSemigroupal[A, B, C],
  )
}

object CsvRecordEncoderLaws {

  case class A(v: Boolean)
  object A {
    val instance = A(v = true)

    implicit val encoder: CsvRecordEncoder[A] = CsvRecordEncoder.derived
  }

  case class B(v: Int)
  object B {
    val instance = B(42)

    implicit val encoder: CsvRecordEncoder[B] = CsvRecordEncoder.derived
  }

  case class C(v: String)
  object C {
    val instance = C("a string")

    implicit val encoder: CsvRecordEncoder[C] = CsvRecordEncoder.derived
  }

  implicit val arbitraryA: Arbitrary[A] = Arbitrary { Arbitrary.arbBool.arbitrary.map(A(_)) }
  implicit val arbitraryFA: Arbitrary[CsvRecordEncoder[A]] = Arbitrary { Gen.const(A.encoder) }

  implicit val arbitraryB: Arbitrary[B] = Arbitrary { Arbitrary.arbInt.arbitrary.map(B(_)) }
  implicit val arbitraryFB: Arbitrary[CsvRecordEncoder[B]] = Arbitrary { Gen.const(B.encoder) }

  implicit val arbitraryC: Arbitrary[C] = Arbitrary { Arbitrary.arbString.arbitrary.map(C(_)) }
  implicit val arbitraryFC: Arbitrary[CsvRecordEncoder[C]] = Arbitrary { Gen.const(C.encoder) }

  implicit val cogenA: Cogen[A] = Cogen.cogenBoolean.contramap(_.v)
  implicit val cogenB: Cogen[B] = Cogen.cogenInt.contramap(_.v)
  implicit val cogenC: Cogen[C] = Cogen.cogenString.contramap(_.v)

  implicit val eqA: Eq[CsvRecordEncoder[A]] = Eq.instance { case (x, y) =>
    val v = A.instance
    x.numFields == y.numFields && x.encode(v) == y.encode(v)
  }

  implicit val eqB: Eq[CsvRecordEncoder[B]] = Eq.instance { case (x, y) =>
    val v = B.instance
    x.numFields == y.numFields && x.encode(v) == y.encode(v)
  }

  implicit val eqC: Eq[CsvRecordEncoder[C]] = Eq.instance { case (x, y) =>
    val v = C.instance
    x.numFields == y.numFields && x.encode(v) == y.encode(v)
  }

  implicit val eqABC: Eq[CsvRecordEncoder[(A, B, C)]] = Eq.instance { case (x, y) =>
    val v = (A.instance, B.instance, C.instance)
    x.numFields == y.numFields && x.encode(v) == y.encode(v)
  }
}
