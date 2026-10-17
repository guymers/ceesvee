package ceesvee

trait CsvHeaderScalaVersion { self: CsvHeader.type =>

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
}
