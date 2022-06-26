package ceesvee

import scala.annotation.switch

object CsvWriter {

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[T](
    header: Iterable[String],
    rows: Iterator[T],
  )(implicit E: CsvRecordEncoder[T]): Iterator[String] = {
    Iterator(fieldsToLine(header)) ++ encode(rows)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[T](
    rows: Iterator[T],
  )(implicit E: CsvRecordEncoder[T]): Iterator[String] = {
    rows.map(E.encode(_)).map(fieldsToLine(_))
  }

  def fieldsToLine(fields: Iterable[String]): String = {
    fields.map(quoteField(_)).mkString(",")
  }

  def quoteField(field: String): String = {
    val _field = field.replaceAll("\"", "\"\"")
    if (needsQuoting(field)) s"\"${_field}\"" else _field
  }

  def needsQuoting(s: String): Boolean = {
    s.headOption.exists(isWhitespace(_)) ||
    s.lastOption.exists(isWhitespace(_)) ||
    containsSpecialCharacter(s)
  }

  def isWhitespace(c: Char): Boolean = {
    c == ' ' || c == '\t'
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
