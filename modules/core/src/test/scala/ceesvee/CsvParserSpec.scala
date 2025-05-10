package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object CsvParserSpec extends ZIOSpecDefault with CsvParserParserSuite with CsvParserLineSuite {

  override val spec = suite("CsvParser")(
    parserSuite,
    suite("split strings")(
      test("trailing new lines") {
        val strings = List(
          "abc\r",
          "def\r",
          "\nghi\r\n",
          "jkl",
          "\nmno",
        )
        val (state, lines) = CsvParser.splitStrings(strings, CsvParser.State.initial)
        assertTrue(lines == List("abc\rdef", "ghi", "jkl")) &&
        assertTrue(state.leftover == "mno")
      },
      test("trailing double quotes") {
        val strings = List(
          "a,\"b\"",
          ",c,\"d\"\"e\",\"",
          "\"",
          "\nfg\"",
        )
        val (state, lines) = CsvParser.splitStrings(strings, CsvParser.State.initial)
        val strings2 = List(
          "\n\"\"\"",
          "\n\"hi\"\"",
        )
        val (state2, lines2) = CsvParser.splitStrings(strings2, state)
        val strings3 = List(
          "j\"",
          "\nmno",
        )
        val (state3, lines3) = CsvParser.splitStrings(strings3, state2)
        assertTrue(
          lines == List("""a,"b",c,"d""e","""""),
          state.insideQuoteIndex == 2,
          state.leftover == "fg\"",
        ) &&
        assertTrue(
          lines2 == List("fg\"\n\"\"\""),
          state2.insideQuoteIndex == 0,
          state2.leftover == "\"hi\"\"",
        ) &&
        assertTrue(
          lines3 == List("\"hi\"\"j\""),
          state3.insideQuoteIndex == -9,
          state3.leftover == "mno",
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
        val (state, lines) = CsvParser.splitStrings(strings, CsvParser.State.initial)
        assertTrue(lines == List(
          "a\"b\"c",
          "d\"\ne\r\nf\"",
          "g\"hi\r\"\"jkl\"",
        )) &&
        assertTrue(state.leftover == "nmno")
      },
      // TODO property based tests
    ),
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
