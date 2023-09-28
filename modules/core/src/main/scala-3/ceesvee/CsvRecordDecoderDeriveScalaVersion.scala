package ceesvee

import scala.collection.immutable.SortedMap
import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.Mirror

trait CsvRecordDecoderDeriveScalaVersion { self: CsvRecordDecoder.type =>

  inline def summonAll[T <: Tuple]: List[CsvRecordDecoder[?]] = inline erasedValue[T] match {
    case _: EmptyTuple => Nil
    case _: (t *: ts) => summonInline[CsvRecordDecoder[t]] :: summonAll[ts]
  }

  inline def derived[A](using m: Mirror.ProductOf[A]): CsvRecordDecoder[A] = {
    lazy val decoders = summonAll[m.MirroredElemTypes]
    new CsvRecordDecoderInstance[A](m, decoders)
  }
}

class CsvRecordDecoderInstance[A](
  m: Mirror.ProductOf[A],
  decoders: => List[CsvRecordDecoder[?]],
) extends CsvRecordDecoder[A] {
  private lazy val instances = decoders
  private lazy val length = instances.length

  override lazy val numFields = instances.foldLeft(0)(_ + _.numFields)

  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
  ))
  override def decode(fields: IndexedSeq[String]) = {
    val errs = SortedMap.newBuilder[Int, CsvRecordDecoder.Errors.Error]
    val values = Array.ofDim[Any](length)

    var index = 0
    var offset = 0
    instances.foreach { decoder =>
      val num = decoder.numFields
      decoder.decode(fields.slice(offset, offset + num)) match {
        case Left(error) => error.errors.foreachEntry { case (k, v) => errs.addOne((k + offset, v)) }
        case Right(v) => values.update(index, v)
      }
      index = index + 1
      offset = offset + num
    }

    val errs_ = errs.result()
    if (errs_.nonEmpty) Left(CsvRecordDecoder.Errors(fields, errs_))
    else Right(m.fromProduct(Tuple.fromArray(values)))
  }
}
