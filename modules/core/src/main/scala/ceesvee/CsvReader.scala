package ceesvee

object CsvReader {
  import CsvParser.parse

  trait Options extends CsvParser.Options
  object Options {

    case class Impl(
      maximumLineLength: Int,
    ) extends Options

    def apply(
      maximumLineLength: Int,
    ): Impl = Impl(
      maximumLineLength = maximumLineLength,
    )
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   */
  @throws[CsvParser.Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  def decodeWithHeader[T](
    in: Iterator[String],
    header: CsvHeader[T],
    options: Options,
  ): Either[CsvHeader.MissingHeaders, Iterator[Either[CsvRecordDecoder.Error, T]]] = {
    @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.Var"))
    var decoder: CsvHeader.Decoder[T] = null

    try {
      val iterator = parse[List](in, options).flatMap { fields =>
        if (decoder == null) {
          header.create(fields) match {
            case Left(err: CsvHeader.MissingHeaders) => throw err
            case Right(_decoder) =>
              decoder = _decoder
              Iterator.empty
          }
        } else {
          Iterator(decoder.decode(fields))
        }
      }
      Right(iterator)
    } catch {
      case e: CsvHeader.MissingHeaders => Left(e)
    }
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * The given strings must contain new lines as this method splits on them.
   *
   * Blank lines and lines starting with '#' are ignored.
   */
  @throws[CsvParser.Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def decode[T](
    in: Iterator[String],
    options: Options,
  )(implicit D: CsvRecordDecoder[T]): Iterator[Either[CsvRecordDecoder.Error, T]] = {
    parse[List](in, options).map(fields => D.decode(fields))
  }
}
