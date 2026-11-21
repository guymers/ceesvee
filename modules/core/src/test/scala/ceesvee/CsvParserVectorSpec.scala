package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.nio.charset.StandardCharsets

object CsvParserVectorSpec extends ZIOSpecDefault
  with CsvParserParserSuite
  with CsvSplitStringsSuite[CsvParserVector.State]
  with CsvParserLineSuite {

  private val charset = StandardCharsets.UTF_8

  private val parserIteratorSuite = suite("parse Iterator contract")(
    test("empty chunks are ignored") {
      val input = Iterator(Array.emptyByteArray, "a,b\n".getBytes(charset), Array.emptyByteArray)
      val it = CsvParserVector.parse[List](input, CsvParser.Options.Defaults)

      assertTrue(it.toList == List(List("a", "b")))
    },
    test("only empty chunks") {
      val it = CsvParserVector.parse[List](Iterator(Array.emptyByteArray), CsvParser.Options.Defaults)

      assertTrue(!it.hasNext) &&
      assertTrue(it.toList == Nil)
    },
    test("empty chunks after a complete line") {
      val it = CsvParserVector.parse[List](Iterator("a,b\n".getBytes(charset), Array.emptyByteArray), CsvParser.Options.Defaults)

      assertTrue(it.hasNext) &&
      assertTrue(it.next() == List("a", "b")) &&
      assertTrue(!it.hasNext)
    },
    test("empty chunks after an unterminated line") {
      val it = CsvParserVector.parse[List](Iterator("a,b".getBytes(charset), Array.emptyByteArray), CsvParser.Options.Defaults)

      assertTrue(it.toList == List(List("a", "b")))
    },
    test("next throws NoSuchElementException when exhausted") {
      val it = CsvParserVector.parse[List](Iterator("a,b\n".getBytes(charset)), CsvParser.Options.Defaults)
      val _ = it.next()

      ZIO.attempt(it.next()).either.map { result =>
        assertTrue(result.swap.exists(_.isInstanceOf[NoSuchElementException]))
      }
    },
  )

  override val spec = suite("CsvParserVector")(
    parserSuite,
    splitStringsSuite,
    parseLineSuite,
    parserIteratorSuite,
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
