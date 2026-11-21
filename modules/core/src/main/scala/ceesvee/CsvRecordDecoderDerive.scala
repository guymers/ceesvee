package ceesvee

import scala.collection.immutable.SortedMap
import scala.compiletime.erasedValue
import scala.compiletime.summonInline
import scala.deriving.Mirror

@SuppressWarnings(Array("org.wartremover.warts.Recursion"))
trait CsvRecordDecoderDerive { self: CsvRecordDecoder.type =>

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
  private lazy val instancesLength = instances.length

  override lazy val numFields = instances.foldLeft(0)(_ + _.numFields)

  override def decode(fields: IndexedSeq[String]) = decodeAt(fields, 0, fields.length)

  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
  ))
  override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
    val errs = SortedMap.newBuilder[Int, CsvRecordDecoder.Errors.Error]
    val values = Array.ofDim[Any](instancesLength)

    var index = 0
    var localOffset = 0
    var absoluteOffset = offset

    instances.foreach { decoder =>
      val num = decoder.numFields
      decoder.decodeAt(fields, absoluteOffset, num) match {
        case Left(error) =>
          error.errors.foreachEntry { case (k, v) =>
            // record level errors are not tied to a field so must not be offset
            val index = if (k == CsvRecordDecoder.Errors.RecordIndex) k else k + localOffset
            errs.addOne((index, v))
          }
        case Right(v) => values.update(index, v)
      }
      index = index + 1
      localOffset = localOffset + num
      absoluteOffset = absoluteOffset + num
    }

    val errs_ = errs.result()
    if (errs_.nonEmpty) Left(CsvRecordDecoder.Errors(fields, errs_))
    else Right(m.fromProduct(Tuple.fromArray(values)))
  }
}
