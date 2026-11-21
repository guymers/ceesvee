package ceesvee.fs2

import ceesvee.CsvParser
import fs2.Stream
import zio.Chunk
import zio.Task
import zio.test.ZIOSpecDefault

object CsvParserVectorSpec extends ZIOSpecDefault with ceesvee.CsvParserParserSuite {
  import zio.interop.catz.asyncInstance

  override val spec = suite("CsvParserVector")(
    parserSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = Stream.emits(lines.toSeq).intersperse("\n").chunkLimit(4096).unchunks
    input.covary[Task]
      .through(fs2.text.utf8.encode)
      .through(Fs2CsvParserVector.parse(options))
      .map(_.toList)
      .compile
      .toList
      .map(Chunk.fromIterable(_))
      .refineOrDie { case e: CsvParser.Error => e }
  }
}
