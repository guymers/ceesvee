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
        val line = CsvWriter.fieldsToLine(fields)
        val parsed = CsvParser.parseLine[Chunk](line, CsvParser.Options.Defaults)
        assertTrue(parsed == fields.toChunk)
      }
    },
    test("to and from a field with unicode whitespace") {
      val fields = List(
        "\u000Bfield",
        "field\u000B",
        "\u000Cfield",
        "field\u000C",
        "\u001Cfield",
        "field\u001C",
        "\u2003field",
        "field\u2003",
        "\u3000field",
        "field\u3000",
        "\u2003 a, \"b\" \u000C",
      )

      assertTrue(fields.forall { field =>
        val line = CsvWriter.fieldsToLine(List(field))
        CsvParser.parseLine[List](line, CsvParser.Options.Defaults) == List(field)
      })
    },
  )
}
