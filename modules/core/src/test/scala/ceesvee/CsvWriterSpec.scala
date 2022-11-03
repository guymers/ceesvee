package ceesvee

import zio.test.*

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
  )
}
