package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault

import java.nio.charset.StandardCharsets

object CsvParserVectorSpec extends ZIOSpecDefault with CsvParserParserSuite with CsvParserLineSuite {

  private val charset = StandardCharsets.UTF_8

  override val spec = suite("CsvParserVector")(
    parserSuite,
    parseLineSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = lines.mkString("\n").grouped(8192).map(_.getBytes(charset))
    val result = CsvParserVector.parse[List](input, charset, options)
    ZIO.succeed(Chunk.fromIterator(result))
  }

  override protected def parseLine(line: String, options: CsvParser.Options) = {
    CsvParserVector.parseLine[List](line.getBytes(charset), charset, options)
  }
}
