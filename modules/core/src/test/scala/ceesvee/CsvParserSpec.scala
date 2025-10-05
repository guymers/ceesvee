package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object CsvParserSpec extends ZIOSpecDefault
  with CsvParserParserSuite
  with CsvSplitStringsSuite[CsvParser.State]
  with CsvParserLineSuite {

  override val spec = suite("CsvParser")(
    parserSuite,
    splitStringsSuite,
    parseLineSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = lines.mkString("\n").grouped(8192)
    val result = CsvParser.parse[List](input, options)
    ZIO.succeed(Chunk.fromIterator(result))
  }

  override protected def parseLine(line: String, options: CsvParser.Options) = {
    CsvParser.parseLine[List](line, options)
  }

  override protected def splitStrings(strings: List[String], state: CsvParser.State) = CsvParser.splitStrings(strings, state)

  override protected def initialState = CsvParser.State.initial
  override protected def stateLeftover(s: CsvParser.State) = s.leftover
  override protected def stateInsideQuoteIndex(s: CsvParser.State) = s.insideQuoteIndex
}

trait CsvParserParserSuite { self: ZIOSpecDefault =>

  protected def parse(
    lines: Iterable[String],
    options: CsvParser.Options,
  ): ZIO[Any, Throwable, Chunk[List[String]]]

  protected def parserSuite = suite("parser")(
    test("lots") {
      def line(i: Int) = List("basic string", " \"quoted \nstring\" ", i.toString, "456.789", "true").mkString(",")

      val lines = (1 to 10).map(line(_))
      parse(lines, CsvParser.Options.Defaults).map { result =>
        assertTrue(result.length == 10)
      }
    },
    suite("comment prefix")({
      val lines = List(
        "a,b,c",
        "#a,b,c",
        "#",
        " #",
        "d,e,f",
      )

      test("no comments") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = None, trim = CsvParser.Options.Trim.False)
        parse(lines, opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List("#a", "b", "c"),
            List("#"),
            List(" #"),
            List("d", "e", "f"),
          ))
        }
      } ::
      test("false") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"))
        parse(lines, opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List("d", "e", "f"),
          ))
        }
      } :: Nil
    }),
    suite("skip blank rows")({
      val lines = List(
        "a,b,c",
        "",
        " ",
        "d,e,f",
      )

      test("true") {
        val opts = CsvParser.Options.Defaults.copy(skipBlankRows = true)
        parse(lines, opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List("d", "e", "f"),
          ))
        }
      } ::
      test("false") {
        val opts = CsvParser.Options.Defaults.copy(skipBlankRows = false)
        parse(lines, opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List(""),
            List(""),
            List("d", "e", "f"),
          ))
        }
      } :: Nil
    }),
  )
}

trait CsvSplitStringsSuite[S] { self: ZIOSpecDefault =>

  protected def splitStrings(strings: List[String], state: S): (S, List[String])

  protected def initialState: S
  protected def stateLeftover(s: S): String
  protected def stateInsideQuoteIndex(s: S): Int

  protected def splitStringsSuite = {
    suite("split strings")(
      test("trailing new lines") {
        val strings = List(
          "abc\r",
          "def\r",
          "\nghi\r\n",
          "jkl",
          "\nmno",
        )
        val (state, lines) = splitStrings(strings, initialState)
        assertTrue(lines == List("abc\rdef", "ghi", "jkl")) &&
        assertTrue(stateLeftover(state) == "mno")
      },
      test("trailing new lines aligned to vector boundary") {
        val strings = List(
          "012345678901234567890123456789012345678901234567890123456789abc\r",
          "012345678901234567890123456789012345678901234567890123456789abc\r",
          "\n012345678901234567890123456789012345678901234567890123456789ab\n",
          "012345678901234567890123456789012345678901234567890123456789abcd",
          "\nmno",
        )
        val (state, lines) = splitStrings(strings, initialState)
        assertTrue(lines == List(
          "012345678901234567890123456789012345678901234567890123456789abc\r012345678901234567890123456789012345678901234567890123456789abc",
          "012345678901234567890123456789012345678901234567890123456789ab",
          "012345678901234567890123456789012345678901234567890123456789abcd",
        )) &&
        assertTrue(stateLeftover(state) == "mno")
      },
      test("trailing double quotes") {
        val strings = List(
          "a,\"b\"",
          ",c,\"d\"\"e\",\"",
          "\"",
          "\nfg\"",
        )
        val (state, lines) = splitStrings(strings, initialState)
        val strings2 = List(
          "\n\"\"\"",
          "\n\"hi\"\"",
        )
        val (state2, lines2) = splitStrings(strings2, state)
        val strings3 = List(
          "j\"",
          "\nmno",
        )
        val (state3, lines3) = splitStrings(strings3, state2)
        assertTrue(
          lines == List("""a,"b",c,"d""e","""""),
          stateInsideQuoteIndex(state) == 2,
          stateLeftover(state) == "fg\"",
        ) &&
        assertTrue(
          lines2 == List("fg\"\n\"\"\""),
          stateInsideQuoteIndex(state2) == 0,
          stateLeftover(state2) == "\"hi\"\"",
        ) &&
        assertTrue(
          lines3 == List("\"hi\"\"j\""),
          stateInsideQuoteIndex(state3) == -9,
          stateLeftover(state3) == "mno",
        )
      },
      test("quotes and new lines") {
        val strings = List(
          "a\"b\"c\n",
          "d\"\ne\r\nf\"\n",
          "g\"hi\r\"",
          "\"jkl\"",
          "\nnmno",
        )
        val (state, lines) = splitStrings(strings, initialState)
        assertTrue(lines == List(
          "a\"b\"c",
          "d\"\ne\r\nf\"",
          "g\"hi\r\"\"jkl\"",
        )) &&
        assertTrue(stateLeftover(state) == "nmno")
      },
      // TODO property based tests
    )
  }
}

trait CsvParserLineSuite { self: ZIOSpecDefault =>

  protected def parseLine(line: String, options: CsvParser.Options): List[String]

  protected def parseLineSuite = {
    import CsvParser.Options

    suite("parse line")(
      suite("escape character")(
        test("double quote") {
          val line = """a,"b""c",d,e"f"""
          assertTrue(parseLine(line, Options.Defaults) == List("a", """b"c""", "d", "e\"f"))
        },
      ),
      suite("trim")({
        val line = """abc, def,ghi , jkl , " mno ", """

        test("true") {
          val opts = Options.Defaults.copy(trim = Options.Trim.True)
          assertTrue(parseLine(line, opts) == List("abc", "def", "ghi", "jkl", " mno ", ""))
        } ::
        test("false") {
          val opts = Options.Defaults.copy(trim = Options.Trim.False)
          assertTrue(parseLine(line, opts) == List("abc", " def", "ghi ", " jkl ", " mno ", " "))
        } ::
        test("start") {
          val opts = Options.Defaults.copy(trim = Options.Trim.Start)
          assertTrue(parseLine(line, opts) == List("abc", "def", "ghi ", "jkl ", " mno ", ""))
        } ::
        test("end") {
          val opts = Options.Defaults.copy(trim = Options.Trim.End)
          assertTrue(parseLine(line, opts) == List("abc", " def", "ghi", " jkl", " mno ", ""))
        } :: Nil
      }),
      test("complex") {
        val line = "abc, def ,,\" g,\"\"h\"\",\ti\" , "
        val result = parseLine(line, Options.Defaults)
        assertTrue(result == List("abc", "def", "", " g,\"h\",\ti", ""))
      },
      test("json") {
        val line = """abc,"{""data"": {""message"": ""blah \""quoted\""\n  pos 123""}, ""type"": ""unhandled""}",xyz"""
        val result = parseLine(line, Options.Defaults)
        assertTrue(result == List("abc", """{"data": {"message": "blah \"quoted\"\n  pos 123"}, "type": "unhandled"}""", "xyz"))
      },
    )
  }
}
