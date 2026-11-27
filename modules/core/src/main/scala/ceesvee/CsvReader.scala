package ceesvee

object CsvReader {
  import CsvParser.parse

  trait Options extends CsvParser.Options
  object Options {

    val Defaults: Impl = apply(
      commentPrefix = CsvParser.Options.Defaults.commentPrefix,
      maximumLineLength = CsvParser.Options.Defaults.maximumLineLength,
      skipBlankRows = CsvParser.Options.Defaults.skipBlankRows,
      trim = CsvParser.Options.Defaults.trim,
      delimiter = CsvParser.Options.Defaults.delimiter,
    )

    case class Impl(
      commentPrefix: Option[String],
      maximumLineLength: Int,
      skipBlankRows: Boolean,
      trim: CsvParser.Options.Trim,
      delimiter: CsvParser.Options.Delimiter,
    ) extends Options

    def apply(
      commentPrefix: Option[String],
      maximumLineLength: Int,
      skipBlankRows: Boolean,
      trim: CsvParser.Options.Trim,
      delimiter: CsvParser.Options.Delimiter,
    ): Impl = Impl(
      commentPrefix = commentPrefix,
      maximumLineLength = maximumLineLength,
      skipBlankRows = skipBlankRows,
      trim = trim,
      delimiter = delimiter,
    )
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   *
   * The first record is consumed as the header, so it is read when this method
   * is called.
   *
   * The input strings must contain new lines as they are split on them. Line
   * oriented streams such as `Files.lines` or `Source.getLines` must not be
   * used directly: their chunks do not contain a new line and every line is
   * joined into a single record.
   *
   * A UTF-8 BOM is not stripped: remove it before calling this method.
   */
  @throws[CsvParser.Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def decodeWithHeader[T](
    in: Iterator[String],
    header: CsvHeader[T],
    options: Options,
  ): Either[CsvHeader.MissingHeaders, Iterator[Either[CsvHeader.Errors, T]]] = {
    val records = parse[IndexedSeq](in, options)

    if (!records.hasNext) {
      Left(CsvHeader.MissingHeaders(header.headers))
    } else {
      header.create(records.next()).map(decoder => records.map(decoder.decode))
    }
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * The given strings must contain new lines as this method splits on them.
   *
   * Blank lines and lines starting with '#' are ignored.
   *
   * A UTF-8 BOM is not stripped: remove it before calling this method.
   */
  @throws[CsvParser.Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def decode[T](
    in: Iterator[String],
    options: Options,
  )(using D: CsvRecordDecoder[T]): Iterator[Either[CsvRecordDecoder.Errors, T]] = {
    parse[IndexedSeq](in, options).map(fields => D.decode(fields))
  }
}
