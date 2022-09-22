package ceesvee

import scala.collection.mutable
import scala.util.control.NoStackTrace

final class CsvHeader[T] private (
  val headers: ::[String],
  private[ceesvee] val D: CsvRecordDecoder[T],
) {

  def create(headerFields: Iterable[String]): Either[CsvHeader.MissingHeaders, CsvHeader.Decoder[T]] = {
    val headerFieldsWithIndex = headerFields.zipWithIndex.toMap
    val (missing, ordering) = headers.partitionMap { header =>
      headerFieldsWithIndex.get(header).toRight(header)
    }

    missing.sorted match {
      case Nil => Right(CsvHeader.Decoder.create(headers, ordering)(D))
      case m @ ::(_, _) => Left(CsvHeader.MissingHeaders(m))
    }
  }
}
object CsvHeader {

  final case class MissingHeaders(missing: ::[String])
    extends RuntimeException(s"Missing headers: ${missing.mkString(", ")}")
    with NoStackTrace

  /**
   * A record decoder that decodes fields based on the names of the headers
   * provided.
   */
  def create[T](headers: ::[String])(implicit D: CsvRecordDecoder[T]): CsvHeader[T] = {
    require(headers.sizeIs == D.decoders.length) // TODO compile time error / better construction

    new CsvHeader[T](headers, D)
  }

  final class Decoder[A] private (
    val headers: ::[String],
    private[ceesvee] val decoders: List[Option[(CsvFieldDecoder[?], Int)]],
    private[ceesvee] val lift: Iterable[Any] => A,
  ) {
    private val _decode = CsvRecordDecoder.decode(decoders, lift)

    def decode(fields: Iterable[String]): Either[CsvRecordDecoder.Error, A] = _decode(fields)
  }
  object Decoder {

    /**
     * A record decoder that re-orders the record to match the expected
     * ordering.
     *
     * @param ordering
     *   maps the expected index to the index of the header column.
     */
    @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
    def create[T](headers: ::[String], ordering: Seq[Int])(implicit D: CsvRecordDecoder[T]): Decoder[T] = {
      if (ordering.zipWithIndex.forall({ case (a, b) => a == b })) {
        new Decoder[T](headers, D._decoders, D.lift)
      } else {
        val decodersByIndex = D.decoders.zipWithIndex.map(_.swap).toMap
        val size = ordering.maxOption.map(_ + 1).getOrElse(0)
        val lb = mutable.ListBuffer.fill[Option[(CsvFieldDecoder[?], Int)]](size)(None)

        ordering.zipWithIndex.foreach { case (fieldIndex, decoderIndex) =>
          lb.update(fieldIndex, Some((decodersByIndex(decoderIndex), decoderIndex)))
        }
        val decoders = lb.toList

        new Decoder[T](headers, decoders, D.lift)
      }
    }
  }
}
