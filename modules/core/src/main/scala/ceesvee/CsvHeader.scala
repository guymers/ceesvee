package ceesvee

import scala.collection.immutable.ArraySeq
import scala.collection.immutable.SortedMap
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

  final case class Error(
    raw: Map[String, String],
    errors: SortedMap[String, CsvRecordDecoder.Error.Field],
  ) extends RuntimeException({
      val reasons = errors.toList.map({ case (h, e) => s"column $h ${e.toString}" })
      s"Failed to decode ${raw.mkString(",").take(64)} because: ${reasons.toString}"
    }) with NoStackTrace

  /**
   * A record decoder that decodes fields based on the names of the headers
   * provided.
   */
  def create[T](headers: ::[String])(implicit D: CsvRecordDecoder[T]): CsvHeader[T] = {
    require(headers.sizeIs == D.numFields) // TODO compile time error / better construction

    new CsvHeader[T](headers, D)
  }

  sealed trait Decoder[A] {
    def withHeaders(fields: IndexedSeq[String]): Map[String, String]
    def decode(fields: IndexedSeq[String]): Either[Error, A]
  }
  object Decoder {

    /**
     * A record decoder that re-orders the record to match the expected
     * ordering.
     *
     * @param ordering
     *   maps the expected index to the index of the header column.
     */
    private[ceesvee] def create[T](
      headers: ::[String],
      ordering: Seq[Int],
    )(implicit D: CsvRecordDecoder[T]): Decoder[T] = {

      val length = ordering.length
      val headerIndices = ordering.zip(headers)
      val orderingIndices = ordering.zipWithIndex.map(_.swap)

      val orderingToHeader = {
        val m = headerIndices.toMap
        orderingIndices.flatMap { case (index, fieldIndex) =>
          m.get(fieldIndex).map((index, _))
        }.toMap
      }

      def convertError(error: CsvRecordDecoder.Error, fields: Map[String, String]) = {
        val errors = error.errors.map { case (i, error) =>
          orderingToHeader.getOrElse(i, s"<${i.toString}>") -> error
        }
        Error(fields, errors)
      }

      if (ordering.zipWithIndex.forall({ case (a, b) => a == b })) {
        new Decoder[T] {
          override def withHeaders(fields: IndexedSeq[String]) = headers.iterator.zip(fields).toMap
          override def decode(fields: IndexedSeq[String]) = D.decode(fields)
            .left.map(convertError(_, withHeaders(fields)))
        }
      } else {
        new Decoder[T] {
          override def withHeaders(fields: IndexedSeq[String]) = {
            val fieldsLength = fields.length

            headerIndices.flatMap { case (fieldIndex, header) =>
              if (fieldIndex < fieldsLength) Some((header, fields(fieldIndex))) else None
            }.toMap
          }
          override def decode(fields: IndexedSeq[String]) = {
            val fieldsLength = fields.length

            val reordered = Array.ofDim[String](length)
            orderingIndices.foreach { case (index, fieldIndex) =>
              val value =
                if (fieldIndex < fieldsLength) fields(fieldIndex)
                else "" // if the row is too short just use an empty string
              reordered.update(index, value)
            }
            D.decode(ArraySeq.unsafeWrapArray(reordered))
              .left.map(convertError(_, withHeaders(fields)))
          }
        }
      }
    }
  }
}
