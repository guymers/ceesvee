package ceesvee

import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.Mirror

trait CsvRecordEncoderDeriveScalaVersion { self: CsvRecordEncoder.type =>

  inline def summonAll[T <: Tuple]: List[CsvRecordEncoder[_]] = {
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonInline[CsvRecordEncoder[t]] :: summonAll[ts]
    }
  }

  inline def derived[A](using m: Mirror.ProductOf[A]): CsvRecordEncoder[A] = {
    lazy val instances = summonAll[m.MirroredElemTypes]

    new CsvRecordEncoder[A] {
      override val numFields = instances.foldLeft(0)(_ + _.numFields)
      override def encode(a: A) = {
        val builder = IndexedSeq.newBuilder[String]
        builder.sizeHint(numFields)

        instances.zipWithIndex.foreach { case (p: CsvRecordEncoder[a], index) =>
          val v = a.asInstanceOf[Product].productElement(index).asInstanceOf[a]
          builder.addAll(p.encode(v))
        }

        builder.result()
      }
    }
  }
}
