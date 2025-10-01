package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object CsvParserSpec extends ZIOSpecDefault with CsvParserParserSuite {

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

  private def parseLineSuite = {
    import CsvParser.parseLine
    import CsvParser.Options

    suite("parse line")(
      suite("escape character")(
        test("double quote") {
          val line = """a,"b""c",d,e"f"""
          assertTrue(parseLine[List](line, Options.Defaults) == List("a", """b"c""", "d", "e\"f"))
        },
      ),
      suite("trim")({
        val line = """abc, def,ghi , jkl , " mno ", """

        test("true") {
          val opts = Options.Defaults.copy(trim = Options.Trim.True)
          assertTrue(parseLine[List](line, opts) == List("abc", "def", "ghi", "jkl", " mno ", ""))
        } ::
        test("false") {
          val opts = Options.Defaults.copy(trim = Options.Trim.False)
          assertTrue(parseLine[List](line, opts) == List("abc", " def", "ghi ", " jkl ", " mno ", " "))
        } ::
        test("start") {
          val opts = Options.Defaults.copy(trim = Options.Trim.Start)
          assertTrue(parseLine[List](line, opts) == List("abc", "def", "ghi ", "jkl ", " mno ", ""))
        } ::
        test("end") {
          val opts = Options.Defaults.copy(trim = Options.Trim.End)
          assertTrue(parseLine[List](line, opts) == List("abc", " def", "ghi", " jkl", " mno ", ""))
        } :: Nil
      }),
      test("complex") {
        val line = "abc, def ,,\" g,\"\"h\"\",\ti\" , "
        val result = parseLine[List](line, Options.Defaults)
        assertTrue(result == List("abc", "def", "", " g,\"h\",\ti", ""))
      },
      test("json") {
        val line = """abc,"{""data"": {""message"": ""blah \""quoted\""\n  pos 123""}, ""type"": ""unhandled""}",xyz"""
        val result = parseLine[List](line, Options.Defaults)
        assertTrue(result == List("abc", """{"data": {"message": "blah \"quoted\"\n  pos 123"}, "type": "unhandled"}""", "xyz"))
      },
    )
  }

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = lines.mkString("\n").grouped(8192)
    val result = CsvParser.parse[List](input, options)
    ZIO.succeed(Chunk.fromIterator(result))
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
