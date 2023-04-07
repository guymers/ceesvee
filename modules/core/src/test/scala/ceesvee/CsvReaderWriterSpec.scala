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
  )
}
