package ceesvee

import ceesvee.test.CsvTestHelper
import zio.Chunk
import zio.duration.*
import zio.test.*

object CsvReaderWriterSpec extends DefaultRunnableSpec {

  override val spec = suite("CsvReaderWriter")(
    testM("to and from a record") {
      check(CsvTestHelper.gen.fields) { fields =>
        val line = CsvWriter.fieldsToLine(fields)
        val parsed = CsvParser.parseLine[Chunk](line)
        assertTrue(parsed == fields.toChunk)
      }
    },
  )

  override val aspects = List(
    TestAspect.timeout(15.seconds),
  )
}
