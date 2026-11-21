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
      case Nil => Right(CsvHeader.Decoder.create(headers, ordering)(using D))
      case m @ ::(_, _) => Left(CsvHeader.MissingHeaders(m))
    }
  }
}
object CsvHeader {

  final case class MissingHeaders(missing: ::[String])
    extends RuntimeException(s"Missing headers: ${missing.mkString(", ")}")
    with NoStackTrace

  final case class Errors(
    raw: Map[String, String],
    errors: SortedMap[String, CsvRecordDecoder.Errors.Error],
  ) extends RuntimeException({
      val reasons = errors.toList.map({ case (h, e) => s"column $h ${e.toString}" })
      s"Failed to decode ${raw.mkString(",").take(64)} because: ${reasons.mkString(";")}"
    }) with NoStackTrace

  /**
   * A record decoder that decodes fields based on the names of the headers
   * provided.
   *
   * The number of headers must equal the number of fields `D` decodes, which is
   * checked at runtime.
   */
  def create[T](headers: ::[String])(using D: CsvRecordDecoder[T]): CsvHeader[T] = {
    createUnsafe(headers, D)
  }

  /**
   * A record decoder that decodes fields based on the names of the headers
   * provided.
   *
   * The number of headers must equal the number of fields `D` decodes, which is
   * checked at runtime.
   */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def createFromTuple[T, H <: Tuple](headers: H)(using
    D: CsvRecordDecoder[T],
    evNonEmpty: H <:< NonEmptyTuple,
    evAllStrings: Tuple.Union[H] <:< String,
  ): CsvHeader[T] = {
    val nonEmpty: NonEmptyTuple = evNonEmpty(headers)
    val cons = ::(nonEmpty.head.asInstanceOf[String], nonEmpty.tail.toList.asInstanceOf[List[String]])

    createUnsafe(cons, D)
  }

  private[ceesvee] def createUnsafe[T](headers: ::[String], D: CsvRecordDecoder[T]): CsvHeader[T] = {
    require(headers.sizeIs == D.numFields)
    new CsvHeader[T](headers, D)
  }

  sealed trait Decoder[A] {
    def withHeaders(fields: IndexedSeq[String]): Map[String, String]
    def decode(fields: IndexedSeq[String]): Either[Errors, A]
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
    )(using D: CsvRecordDecoder[T]): Decoder[T] = {

      val length = ordering.length
      val headerIndices = ordering.zip(headers)
      val orderingIndices = ordering.zipWithIndex.map(_.swap)

      val orderingToHeader = {
        val m = headerIndices.toMap
        orderingIndices.flatMap { case (index, fieldIndex) =>
          m.get(fieldIndex).map((index, _))
        }.toMap
      }

      def convertError(error: CsvRecordDecoder.Errors, fields: Map[String, String]) = {
        val errors = error.errors.map { case (i, error) =>
          orderingToHeader.getOrElse(i, s"<${i.toString}>") -> error
        }
        Errors(fields, errors)
      }

      if (ordering.zipWithIndex.forall({ case (a, b) => a == b })) {
        new Decoder[T] {
          override def withHeaders(fields: IndexedSeq[String]) = headers.iterator.zip(fields).toMap
          override def decode(fields: IndexedSeq[String]) = D.decode(fields)
            .left.map(convertError(_, withHeaders(fields)))
        }
      } else {
        // number of row fields needed to cover every expected header
        val requiredFieldsCount = ordering.maxOption.getOrElse(0) + 1

        new Decoder[T] {
          @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
          override def withHeaders(fields: IndexedSeq[String]) = {
            val fieldsLength = fields.length

            headerIndices.flatMap { case (fieldIndex, header) =>
              if (fieldIndex < fieldsLength) Some((header, fields(fieldIndex))) else None
            }.toMap
          }
          @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
          override def decode(fields: IndexedSeq[String]) = {
            val fieldsLength = fields.length

            val reordered = Array.ofDim[String](length)
            orderingIndices.foreach { case (index, fieldIndex) =>
              val value =
                if (fieldIndex < fieldsLength) fields(fieldIndex)
                else "" // fill so the record decoder can run, replaced with Missing below
              reordered.update(index, value)
            }

            // expected indexes whose column is not present in a row that is too short
            val missing =
              if (fieldsLength >= requiredFieldsCount) {
                SortedMap.empty[Int, CsvRecordDecoder.Errors.Error]
              } else {
                SortedMap.from[Int, CsvRecordDecoder.Errors.Error](orderingIndices.collect {
                  case (index, fieldIndex) if fieldIndex >= fieldsLength =>
                    index -> CsvRecordDecoder.Errors.Field.Missing
                })
              }

            D.decode(ArraySeq.unsafeWrapArray(reordered)) match {
              case Right(value) =>
                if (missing.isEmpty) {
                  Right(value)
                } else {
                  Left(convertError(CsvRecordDecoder.Errors(fields, missing), withHeaders(fields)))
                }
              case Left(error) =>
                val errors = SortedMap.from(error.errors ++ missing)
                Left(convertError(error.copy(errors = errors), withHeaders(fields)))
            }
          }
        }
      }
    }
  }
}
