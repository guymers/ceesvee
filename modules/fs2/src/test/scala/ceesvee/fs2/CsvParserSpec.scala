package ceesvee.fs2

import ceesvee.CsvParser
import fs2.Stream
import zio.Chunk
import zio.Task
import zio.test.ZIOSpecDefault

object CsvParserSpec extends ZIOSpecDefault with ceesvee.CsvParserParserSuite {
  import zio.interop.catz.asyncInstance

  override val spec = suite("CsvParser")(
    parserSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = Stream.emits(lines.toSeq).intersperse("\n").chunkLimit(4096).unchunks
    input.covary[Task]
      .through(Fs2CsvParser.parse(options))
      .map(_.toList)
      .compile
      .toList
      .map(Chunk.fromIterable(_))
  }
}
