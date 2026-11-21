package ceesvee

import scala.annotation.switch

object CsvWriter {

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[T](
    header: Iterable[String],
    rows: Iterator[T],
  )(using CsvRecordEncoder[T]): Iterator[String] = {
    Iterator(fieldsToLine(header)) ++ encode(rows)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[T](
    rows: Iterator[T],
  )(using E: CsvRecordEncoder[T]): Iterator[String] = {
    rows.map(E.encode(_)).map(fieldsToLine(_))
  }

  /**
   * Encode fields into a single CSV line.
   */
  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  def fieldsToLine(fields: Iterable[String]): String = {
    val builder = new StringBuilder()
    val it = fields.iterator

    if (it.hasNext) {
      val _ = appendField(builder, it.next())

      while (it.hasNext) {
        val _ = builder.append(',')
        val _ = appendField(builder, it.next())
      }
    }

    builder.toString
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

  def needsQuoting(s: String): Boolean = {
    s.nonEmpty && (
      Character.isWhitespace(s.charAt(0)) ||
      Character.isWhitespace(s.charAt(s.length - 1)) ||
      containsSpecialCharacter(s)
    )
  }

  @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
  private def appendField(builder: StringBuilder, field: String): Unit = {
    if (needsQuoting(field)) {
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
  private def containsSpecialCharacter(s: String): Boolean = {
    var i = 0
    var contains = false

    while (i < s.length) {
      (s(i): @switch) match {

        case '"' | ',' | '\n' | '\r' =>
          contains = true
          i = s.length

        case _ =>
          i += 1
      }
    }

    contains
  }

}
