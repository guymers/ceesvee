package ceesvee

import scala.collection.immutable.SortedMap
import scala.util.control.NoStackTrace

sealed abstract class CsvRecordDecoder[A] private (
  private[ceesvee] val decoders: List[CsvFieldDecoder[?]],
  private[ceesvee] val lift: Iterable[Any] => A,
) {
  private[ceesvee] val _decoders = decoders.zipWithIndex.map(Some(_))
  private val _decode = CsvRecordDecoder.decode(_decoders, lift)

  final def decode(fields: Iterable[String]): Either[CsvRecordDecoder.Error, A] = _decode(fields)

  final def map[B](f: A => B): CsvRecordDecoder[B] = new CsvRecordDecoder[B](decoders, lift.andThen(f(_))) {}
}
object CsvRecordDecoder {

  final case class Error(
    raw: Iterable[String],
    errors: SortedMap[Int, Error.Field],
  ) extends RuntimeException({
      val reasons = errors.toList.map({ case (i, e) => s"index ${i.toString} ${e.toString}" })
      s"Failed to decode ${raw.mkString(",").take(64)} because: ${reasons.toString}"
    }) with NoStackTrace
  object Error {

    sealed trait Field {
      override val toString = this match {
        case Field.Invalid(error) => error.getMessage
        case Field.Missing => "Missing field"
      }
    }
    object Field {
      final case class Invalid(error: CsvFieldDecoder.Error) extends Field
      case object Missing extends Field
    }
  }

  def apply[T](implicit D: CsvRecordDecoder[T]): CsvRecordDecoder[T] = D

  def derive[T](implicit D: CsvRecordDecoderDerive[T]): CsvRecordDecoder[T] = {
    new CsvRecordDecoder[T](D.decoders, D.lift) {}
  }

  @SuppressWarnings(Array(
    "org.wartremover.warts.AsInstanceOf",
    "org.wartremover.warts.ImplicitParameter",
    "org.wartremover.warts.IterableOps",
  ))
  implicit def field[T](implicit D: => CsvFieldDecoder[T]): CsvRecordDecoder[T] = {
    new CsvRecordDecoder[T](List(D), _.head.asInstanceOf[T]) {}
  }

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures", "org.wartremover.warts.Null"))
  private[ceesvee] def decode[A](
    decoders: List[Option[(CsvFieldDecoder[?], Int)]],
    lift: Iterable[Any] => A,
  ) = {
    val size = decoders.flatten.length
    val decodersWithIndex = decoders.zipWithIndex
    val _decoders = decoders.zipWithIndex.collect { case (Some(v), i) => (v, i) }

    (fields: Iterable[String]) => {

      val _errs = SortedMap.newBuilder[Int, Error.Field]
      val values = Array.ofDim[Any](size)

      def decode(decoder: CsvFieldDecoder[?], vi: Int, field: String, i: Int) = {
        decoder.decode(field) match {
          case Left(error) => _errs.addOne(i -> Error.Field.Invalid(error))
          case Right(v) => values.update(vi, v)
        }
      }

      def missing(i: Int) = _errs.addOne(i -> Error.Field.Missing)

      fields match {
        case fs: IndexedSeq[String] =>
          _decoders.foreach { case ((decoder, vi), i) =>
            if (i >= fs.size) missing(i)
            else decode(decoder, vi, fs.apply(i), i)
          }

        case _ =>
          decodersWithIndex
            .iterator
            .zipAll(fields, null, null)
            .takeWhile({ case (d, _) => d != null })
            .foreach { case ((decoder, i), field) =>
              if (field == null) missing(i)
              else decoder match {
                case None => ()
                case Some((decoder, vi)) => decode(decoder, vi, field, i)
              }
            }
      }

      val errs = _errs.result()
      if (errs.nonEmpty) Left(CsvRecordDecoder.Error(fields, errs))
      else Right(lift(values))
    }
  }
}

final class CsvRecordDecoderDerive[A](
  val decoders: List[CsvFieldDecoder[?]],
  val lift: Iterable[Any] => A,
)
object CsvRecordDecoderDerive extends CsvRecordDecoderDeriveScalaVersion {}
