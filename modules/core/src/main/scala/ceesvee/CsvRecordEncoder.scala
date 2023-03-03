package ceesvee

import ceesvee.util.<:!<

trait CsvRecordEncoder[A] { self =>
  def numFields: Int
  def encode(a: A): IndexedSeq[String]

  final def contramap[B](f: B => A): CsvRecordEncoder[B] = new CsvRecordEncoder[B] {
    override val numFields = self.numFields
    override def encode(b: B) = self.encode(f(b))
  }

  final def product[B](fb: CsvRecordEncoder[B]): CsvRecordEncoder[(A, B)] = new CsvRecordEncoder[(A, B)] {
    override val numFields = self.numFields + fb.numFields
    override def encode(t: (A, B)) = self.encode(t._1) ++ fb.encode(t._2)
  }
}
object CsvRecordEncoder extends CsvRecordEncoder1 {

  def apply[T](implicit E: CsvRecordEncoder[T]): CsvRecordEncoder[T] = E

  private[ceesvee] def createField[T](implicit E: => CsvFieldEncoder[T]): CsvRecordEncoder[T] = {
    new CsvRecordEncoder[T] {
      override val numFields = 1
      override def encode(t: T) = Vector(E.encode(t))
    }
  }
}

// cache common field record encoders
sealed trait CsvRecordEncoder1 extends CsvRecordEncoder2 { self: CsvRecordEncoder.type =>

  implicit val fieldString: CsvRecordEncoder[String] = createField[String]
  implicit val fieldBoolean: CsvRecordEncoder[Boolean] = createField[Boolean]
  implicit val fieldInt: CsvRecordEncoder[Int] = createField[Int]
  implicit val fieldLong: CsvRecordEncoder[Long] = createField[Long]
  implicit val fieldFloat: CsvRecordEncoder[Float] = createField[Float]
  implicit val fieldDouble: CsvRecordEncoder[Double] = createField[Double]
}

sealed trait CsvRecordEncoder2 extends CsvRecordEncoder3 { self: CsvRecordEncoder.type =>

  implicit def field[T](implicit E: CsvFieldEncoder[T]): CsvRecordEncoder[T] = createField[T]
}

sealed trait CsvRecordEncoder3 extends CsvRecordEncoder4 { self: CsvRecordEncoder.type =>

  implicit def optional[T](implicit E: CsvRecordEncoder[T], ev: T <:!< Option[?]): CsvRecordEncoder[Option[T]] = {
    val _ = ev
    val empty = IndexedSeq.fill(E.numFields)("")

    new CsvRecordEncoder[Option[T]] {
      override val numFields = E.numFields
      override def encode(t: Option[T]) = t.fold(empty)(E.encode(_))
    }
  }
}

sealed trait CsvRecordEncoder4 extends CsvRecordEncoderDeriveScalaVersion { self: CsvRecordEncoder.type =>

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  implicit def ContravariantSemigroupalCsvRecordEncoder[F[_[_]]](implicit
    env: CatsContravariantSemigroupal[F],
  ): F[CsvRecordEncoder] = {
    val _ = env

    new cats.ContravariantSemigroupal[CsvRecordEncoder] {
      override def contramap[A, B](fa: CsvRecordEncoder[A])(f: B => A) = fa.contramap(f)
      override def product[A, B](fa: CsvRecordEncoder[A], fb: CsvRecordEncoder[B]) = fa.product(fb)
    }.asInstanceOf[F[CsvRecordEncoder]]
  }
}

// https://blog.7mind.io/no-more-orphans.html
final abstract class CatsContravariantSemigroupal[F[_[_]]]
object CatsContravariantSemigroupal {
  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  @inline implicit final def get: CatsContravariantSemigroupal[cats.ContravariantSemigroupal] = null
}
