package ceesvee.zio

import ceesvee.CsvParser
import zio.stream.ZStream
import zio.test.ZIOSpecDefault

import java.nio.charset.StandardCharsets

object ZioCsvParserVectorSpec extends ZIOSpecDefault with ceesvee.CsvParserParserSuite {

  override val spec = suite("ZioCsvParserVector")(
    parserSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val charset = StandardCharsets.UTF_8
    val input = ZStream.fromIterable(lines).intersperse("\n").rechunk(4096).mapConcat(_.getBytes(charset))
    input
      .via(ZioCsvParserVector.parse(charset, options))
      .map(_.toList)
      .runCollect
      .mapError(e => new RuntimeException(s"failed to parse: $e"))
  }
}
