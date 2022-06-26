package ceesvee

import scala.collection.mutable.ListBuffer

sealed trait CsvRecordEncoder[A] { self =>
  def encode(a: A): Iterable[String]

  final def contramap[B](f: B => A): CsvRecordEncoder[B] = new CsvRecordEncoder[B] {
    override def encode(b: B) = self.encode(f(b))
  }
}
object CsvRecordEncoder {

  def apply[T](implicit E: CsvRecordEncoder[T]): CsvRecordEncoder[T] = E

  def derive[T](implicit E: CsvRecordEncoderDerive[T]): CsvRecordEncoder[T] = {
    new CsvRecordEncoder[T] {
      override def encode(t: T) = E.encode(t)
    }
  }

  implicit def field[T](implicit E: => CsvFieldEncoder[T]): CsvRecordEncoder[T] = {
    new CsvRecordEncoder[T] {
      @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
      override def encode(t: T) = ListBuffer(E.encode(t))
    }
  }
}

trait CsvRecordEncoderDerive[A] {
  def encode(a: A): Iterable[String]
}
object CsvRecordEncoderDerive extends CsvRecordEncoderDeriveScalaVersion {

  def instance[A](f: A => Iterable[String]): CsvRecordEncoderDerive[A] = {
    new CsvRecordEncoderDerive[A] {
      override def encode(a: A) = f(a)
    }
  }
}
