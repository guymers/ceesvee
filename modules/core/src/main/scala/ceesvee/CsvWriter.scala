package ceesvee

// overloading keeps the existing methods while adding options taking variants
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object CsvWriter {

  trait Options {
    def delimiter: CsvParser.Options.Delimiter

    /**
     * The comment prefix used when reading, if any.
     *
     * A record whose first field starts with the comment prefix is quoted so
     * that it is not dropped as a comment when read back.
     */
    def commentPrefix: Option[String]
  }
  object Options {

    val Defaults: Impl = apply(
      delimiter = CsvParser.Options.Defaults.delimiter,
      commentPrefix = CsvParser.Options.Defaults.commentPrefix,
    )

    case class Impl(
      delimiter: CsvParser.Options.Delimiter,
      commentPrefix: Option[String],
    ) extends Options

    def apply(
      delimiter: CsvParser.Options.Delimiter,
      commentPrefix: Option[String],
    ): Impl = Impl(
      delimiter = delimiter,
      commentPrefix = commentPrefix,
    )

    /**
     * Writer options that match the given parser options.
     *
     * Useful when writing data that will be read back with the same options.
     */
    def from(options: CsvParser.Options): Impl = Impl(
      delimiter = options.delimiter,
      commentPrefix = options.commentPrefix,
    )
  }

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[T](
    header: Iterable[String],
    rows: Iterator[T],
  )(using CsvRecordEncoder[T]): Iterator[String] = {
    encodeWithHeader(header, rows, Options.Defaults)
  }

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[T](
    header: Iterable[String],
    rows: Iterator[T],
    options: Options,
  )(using CsvRecordEncoder[T]): Iterator[String] = {
    Iterator(fieldsToLine(header, options)) ++ encode(rows, options)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[T](
    rows: Iterator[T],
  )(using E: CsvRecordEncoder[T]): Iterator[String] = {
    encode(rows, Options.Defaults)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[T](
    rows: Iterator[T],
    options: Options,
  )(using E: CsvRecordEncoder[T]): Iterator[String] = {
    rows.map(E.encode(_)).map(fieldsToLine(_, options))
  }

  /**
   * Encode fields into a single CSV line.
   *
   * A record that would otherwise produce an empty line (no fields, or a single
   * empty field) is encoded as a quoted empty field so that it cannot be
   * mistaken for a blank line or the end of the input when read back.
   */
  def fieldsToLine(fields: Iterable[String]): String = {
    fieldsToLine(fields, Options.Defaults)
  }

  /**
   * Encode fields into a single CSV line.
   *
   * A record that would otherwise produce an empty line (no fields, or a single
   * empty field) is encoded as a quoted empty field so that it cannot be
   * mistaken for a blank line or the end of the input when read back.
   */
  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  def fieldsToLine(fields: Iterable[String], options: Options): String = {
    val builder = new StringBuilder()
    val it = fields.iterator

    if (it.hasNext) {
      val _ = appendField(builder, it.next(), options, isFirst = true)

      while (it.hasNext) {
        val _ = builder.append(delimiterChar(options.delimiter))
        val _ = appendField(builder, it.next(), options, isFirst = false)
      }
    }

    if (builder.isEmpty) QuotedEmpty else builder.toString
  }

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  def quoteField(field: String): String = {
    if (needsQuoting(field)) {
      val builder = new StringBuilder(field.length + 2)
      val _ = appendQuotedField(builder, field)
      builder.toString
    } else {
      field
    }
  }

  /**
   * Whether the given field must be quoted when written with the default comma
   * delimiter.
   */
  def needsQuoting(s: String): Boolean = {
    needsQuotingForDelimiter(s, Comma)
  }

  private def needsQuotingForDelimiter(s: String, delimiter: Char): Boolean = {
    s.nonEmpty && (
      Character.isWhitespace(s.charAt(0)) ||
      Character.isWhitespace(s.charAt(s.length - 1)) ||
      containsSpecialCharacter(s, delimiter)
    )
  }

  private def needsQuotingForField(s: String, options: Options, isFirst: Boolean): Boolean = {
    needsQuotingForDelimiter(s, delimiterChar(options.delimiter)) ||
    (isFirst && isComment(s, options))
  }

  private def isComment(s: String, options: Options): Boolean = {
    options.commentPrefix.filter(_.nonEmpty).exists(s.startsWith(_))
  }

  private def delimiterChar(delimiter: CsvParser.Options.Delimiter): Char = delimiter match {
    case CsvParser.Options.Delimiter.Comma => Comma
    case CsvParser.Options.Delimiter.Tab => '\t'
  }

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private def appendField(builder: StringBuilder, field: String, options: Options, isFirst: Boolean): Unit = {
    if (needsQuotingForField(field, options, isFirst)) {
      appendQuotedField(builder, field)
    } else {
      val _ = builder.append(field)
    }
  }

  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  private def appendQuotedField(builder: StringBuilder, field: String): Unit = {
    val _ = builder.append('"')

    var i = 0
    while (i < field.length) {
      val c = field.charAt(i)

      if (c == '"') {
        val _ = builder.append("\"\"")
      } else {
        val _ = builder.append(c)
      }

      i += 1
    }

    val _ = builder.append('"')
  }

  @SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.While"))
  private def containsSpecialCharacter(s: String, delimiter: Char): Boolean = {
    var i = 0
    var contains = false

    while (i < s.length) {
      val c = s.charAt(i)

      if (c == '"' || c == '\n' || c == '\r' || c == delimiter) {
        contains = true
        i = s.length
      } else {
        i += 1
      }
    }

    contains
  }

  private val Comma = ','
  private val QuotedEmpty = "\"\""
}
