package ceesvee

import ceesvee.util.<:!<

trait CsvRecordEncoder[A] { self =>
  def numFields: Int
  def encode(a: A): IndexedSeq[String]

  final def contramap[B](f: B => A): CsvRecordEncoder[B] = new CsvRecordEncoder[B] {
    override val numFields = self.numFields
    override def encode(b: B) = self.encode(f(b))
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

  implicit def field[T: CsvFieldEncoder]: CsvRecordEncoder[T] = createField[T]
}

sealed trait CsvRecordEncoder3 extends CsvRecordEncoderDeriveScalaVersion { self: CsvRecordEncoder.type =>

  implicit def optional[T](implicit E: CsvRecordEncoder[T], ev: T <:!< Option[?]): CsvRecordEncoder[Option[T]] = {
    val _ = ev
    val empty = IndexedSeq.fill(E.numFields)("")

    new CsvRecordEncoder[Option[T]] {
      override val numFields = E.numFields
      override def encode(t: Option[T]) = t.fold(empty)(E.encode(_))
    }
  }
}
