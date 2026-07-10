package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault

import java.nio.charset.StandardCharsets

object CsvParserVectorSpec extends ZIOSpecDefault
  with CsvParserParserSuite
  with CsvSplitStringsSuite[CsvParserVector.State]
  with CsvParserLineSuite {

  private val charset = StandardCharsets.UTF_8

  override val spec = suite("CsvParserVector")(
    parserSuite,
    splitStringsSuite,
    parseLineSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = lines.mkString("\n").grouped(8192).map(_.getBytes(charset))
    val result = CsvParserVector.parse[List](input, options)
    ZIO.attempt(Chunk.fromIterator(result)).refineOrDie { case e: CsvParser.Error => e }
  }

  override protected def parseLine(line: String, options: CsvParser.Options) = {
    CsvParserVector.parseLine[List](line.getBytes(charset), options)
  }

  override protected def splitStrings(strings: List[String], state: CsvParserVector.State) = {
    strings.foldLeft((state, List.empty[String])) { case ((s, acc), str) =>
      val (nextState, lines) = CsvParserVector.splitBytes[List](str.getBytes(charset), s)
      (nextState, acc ++ lines.map(new String(_, charset)))
    }
  }

  override protected def initialState = CsvParserVector.State.initial
  override protected def stateLeftover(s: CsvParserVector.State) = new String(s.leftover, charset)
}
