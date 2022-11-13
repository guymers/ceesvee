package ceesvee

import scala.collection.immutable.SortedMap
import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.Mirror

trait CsvRecordDecoderDeriveScalaVersion { self: CsvRecordDecoder.type =>

  inline def summonAll[T <: Tuple]: List[CsvRecordDecoder[_]] = {
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts) => summonInline[CsvRecordDecoder[t]] :: summonAll[ts]
    }
  }

  inline def derived[A](using m: Mirror.ProductOf[A]): CsvRecordDecoder[A] = {
    lazy val instances = summonAll[m.MirroredElemTypes]

    new CsvRecordDecoder[A] {
      private val length = instances.length

      override val numFields = instances.foldLeft(0)(_ + _.numFields)
      override def decode(fields: IndexedSeq[String]) = {
        val errs = SortedMap.newBuilder[Int, Errors.Error]
        val values = Array.ofDim[Any](length)

        val _ = instances.foldLeft((0, 0)) { case ((index, offset), p) =>
          val num = p.numFields
          p.decode(fields.slice(offset, offset + num)) match {
            case Left(error) => error.errors.foreach { case (k, v) => errs.addOne((k + offset, v)) }
            case Right(v) => values.update(index, v)
          }
          (index + 1, offset + num)
        }

        val errs_ = errs.result()
        if (errs_.nonEmpty) Left(Errors(fields, errs_))
        else Right(m.fromProduct(Tuple.fromArray(values)))
      }
    }
  }
}
