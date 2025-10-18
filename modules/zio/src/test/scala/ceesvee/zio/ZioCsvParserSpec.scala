package ceesvee.zio

import ceesvee.CsvParser
import zio.stream.ZStream
import zio.test.ZIOSpecDefault

object ZioCsvParserSpec extends ZIOSpecDefault with ceesvee.CsvParserParserSuite {

  override val spec = suite("ZioCsvParser")(
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
