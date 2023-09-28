package ceesvee

import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.Mirror

trait CsvRecordEncoderDeriveScalaVersion { self: CsvRecordEncoder.type =>

  inline def summonAll[T <: Tuple]: List[CsvRecordEncoder[?]] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts) => summonInline[CsvRecordEncoder[t]] :: summonAll[ts]
  }

  inline def derived[A](using m: Mirror.ProductOf[A]): CsvRecordEncoder[A] = {
    lazy val encoders = summonAll[m.MirroredElemTypes]
    new CsvRecordEncoderInstance(encoders)
  }
}

class CsvRecordEncoderInstance[A](encoders: => List[CsvRecordEncoder[?]]) extends CsvRecordEncoder[A] {
  private lazy val instances = encoders

  override lazy val numFields = instances.foldLeft(0)(_ + _.numFields)
  @SuppressWarnings(Array(
    "org.wartremover.warts.AsInstanceOf",
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
  ))
  override def encode(a: A) = {
    val builder = IndexedSeq.newBuilder[String]
    builder.sizeHint(numFields)

    var index = 0
    instances.foreach { case (encoder: CsvRecordEncoder[a]) =>
      val v = a.asInstanceOf[Product].productElement(index).asInstanceOf[a]
      builder.addAll(encoder.encode(v))
      index = index + 1
    }

    builder.result()
  }
}
