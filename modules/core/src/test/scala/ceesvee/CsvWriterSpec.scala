package ceesvee

import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object CsvWriterSpec extends ZIOSpecDefault {
  import CsvWriter.quoteField

  override val spec = suite("CsvWriter")(
    suite("field")(
      suite("quoting")(
        test("not required") {
          assertTrue(quoteField("field") == "field") &&
          assertTrue(quoteField("field blah") == "field blah")
        },
        test("leading whitespace") {
          assertTrue(quoteField(" field") == "\" field\"") &&
          assertTrue(quoteField("\tfield") == "\"\tfield\"")
        },
        test("trailing whitespace") {
          assertTrue(quoteField("field ") == "\"field \"") &&
          assertTrue(quoteField("field\t") == "\"field\t\"")
        },
        suite("unicode whitespace")(
          List('\u000B', '\u000C', '\u001C', '\u2003', '\u3000').map { c =>
            test(f"\\u${c.toInt}%04x") {
              assertTrue(quoteField(s"${c}field") == s"\"${c}field\"") &&
              assertTrue(quoteField(s"field${c}") == s"\"field${c}\"")
            }
          },
        ),
        test("line endings") {
          assertTrue(quoteField("one\ntwo") == "\"one\ntwo\"") &&
          assertTrue(quoteField("one\rtwo") == "\"one\rtwo\"") &&
          assertTrue(quoteField("one\r\ntwo") == "\"one\r\ntwo\"")
        },
        test("comma") {
          assertTrue(quoteField("one,two") == "\"one,two\"")
        },
        test("double quote") {
          assertTrue(quoteField("a \"quoted\" field") == "\"a \"\"quoted\"\" field\"")
        },
      ),
    ),
    suite("line")(
      test("empty record is not an empty line") {
        assertTrue(CsvWriter.fieldsToLine(List.empty) == "\"\"") &&
        assertTrue(CsvWriter.fieldsToLine(List("")) == "\"\"") &&
        assertTrue(CsvParser.parseLine[List]("\"\"", CsvParser.Options.Defaults) == List(""))
      },
      test("trailing empty record is readable") {
        val line = CsvWriter.fieldsToLine(List.empty)
        val records = CsvParser.parse[List](Iterator(s"a\n$line"), CsvParser.Options.Defaults).toList

        assertTrue(records == List(List("a"), List("")))
      },
      test("tab delimiter") {
        val options = CsvWriter.Options(
          delimiter = CsvParser.Options.Delimiter.Tab,
          commentPrefix = None,
        )

        assertTrue(CsvWriter.fieldsToLine(List("a", "b"), options) == "a\tb") &&
        assertTrue(CsvWriter.fieldsToLine(List("a\tb", "c"), options) == "\"a\tb\"\tc") &&
        assertTrue(CsvWriter.fieldsToLine(List("a,b", "c"), options) == "a,b\tc")
      },
      test("comment prefix quotes the first field") {
        val options = CsvWriter.Options(
          delimiter = CsvParser.Options.Delimiter.Comma,
          commentPrefix = Some("#"),
        )
        val parserOptions = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"))

        assertTrue(CsvWriter.fieldsToLine(List("#a", "b"), options) == "\"#a\",b") &&
        assertTrue(CsvWriter.fieldsToLine(List("a", "#b"), options) == "a,#b") &&
        assertTrue(CsvParser.parseLine[List](CsvWriter.fieldsToLine(List("#a", "b"), options), parserOptions) == List("#a", "b"))
      },
      test("options from parser options") {
        val parserOptions = CsvParser.Options.Defaults.copy(
          delimiter = CsvParser.Options.Delimiter.Tab,
          commentPrefix = Some("#"),
        )
        val options = CsvWriter.Options.from(parserOptions)

        assertTrue(CsvWriter.fieldsToLine(List("#a", "b"), options) == "\"#a\"\tb")
      },
    ),
  )
}
