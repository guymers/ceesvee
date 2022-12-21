package ceesvee.zio

import ceesvee.CsvParser
import zio.stream.ZStream
import zio.test.*

object CsvParserSpec extends ZIOSpecDefault with ceesvee.CsvParserParserSuite {

  override val spec = suite("CsvParser")(
    parserSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = ZStream.fromIterable(lines).intersperse("\n").rechunk(4096)
    input
      .via(ZioCsvParser.parse(options))
      .map(_.toList)
      .runCollect
      .mapError(e => new RuntimeException(s"failed to parse: $e"))
  }
}
