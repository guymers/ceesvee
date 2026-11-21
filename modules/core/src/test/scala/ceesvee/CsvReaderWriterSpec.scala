package ceesvee

import ceesvee.test.CsvTestHelper
import zio.Chunk
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

object CsvReaderWriterSpec extends ZIOSpecDefault {

  override val spec = suite("CsvReaderWriter")(
    test("to and from a record") {
      check(CsvTestHelper.gen.fields) { fields =>
        assertFields(fields.toChunk)
      }
    },
    test("to and from a field with unicode whitespace") {
      assertField("\u000Bfield") &&
      assertField("field\u000B") &&
      assertField("\u000Cfield") &&
      assertField("field\u000C") &&
      assertField("\u001Cfield") &&
      assertField("field\u001C") &&
      assertField("\u2003field") &&
      assertField("field\u2003") &&
      assertField("\u3000field") &&
      assertField("field\u3000") &&
      assertField("\u2003 a, \"b\" \u000C")
    },
    test("to and from a record with the tab delimiter") {
      check(CsvTestHelper.gen.fields) { fields =>
        val chunk = fields.toChunk
        val options = CsvParser.Options.Defaults.copy(delimiter = CsvParser.Options.Delimiter.Tab)
        val line = CsvWriter.fieldsToLine(chunk, CsvWriter.Options.from(options))

        assertTrue(CsvParser.parseLine[Chunk](line, options) == chunk)
      }
    },
  )

  private def assertField(field: String) = assertFields(Chunk(field))

  private def assertFields(fields: Chunk[String]) = {
    val line = CsvWriter.fieldsToLine(fields)
    assertTrue(CsvParser.parseLine[Chunk](line, CsvParser.Options.Defaults) == fields)
  }
}
