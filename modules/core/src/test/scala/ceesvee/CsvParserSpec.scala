package ceesvee

import zio.Chunk
import zio.ZIO
import zio.test.Gen
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

object CsvParserSpec extends ZIOSpecDefault
  with CsvParserParserSuite
  with CsvSplitStringsSuite[CsvParser.State]
  with CsvParserLineSuite {

  private val parserIteratorSuite = suite("parse Iterator contract")(
    test("hasNext and next") {
      val lines = List("a,b", "c,d", "e,f")
      val input = lines.mkString("\n").grouped(8192)
      val it = CsvParser.parse[List](input, CsvParser.Options.Defaults)

      assertTrue(it.hasNext) &&
      assertTrue(it.hasNext) && // idempotent
      assertTrue(it.next() == List("a", "b")) &&
      assertTrue(it.hasNext) &&
      assertTrue(it.next() == List("c", "d")) &&
      assertTrue(it.next() == List("e", "f")) &&
      assertTrue(!it.hasNext)
    },
    test("empty input") {
      val it = CsvParser.parse[List](List.empty[String].iterator, CsvParser.Options.Defaults)
      assertTrue(!it.hasNext)
    },
    test("next throws NoSuchElementException when exhausted") {
      val it = CsvParser.parse[List](List("a,b").iterator, CsvParser.Options.Defaults)
      val _ = it.next()
      ZIO.attempt(it.next()).either.map { result =>
        assertTrue(result.swap.exists(_.isInstanceOf[NoSuchElementException]))
      }
    },
  )

  override val spec = suite("CsvParser")(
    parserSuite,
    splitStringsSuite,
    parseLineSuite,
    parserIteratorSuite,
  )

  override protected def parse(lines: Iterable[String], options: CsvParser.Options) = {
    val input = lines.mkString("\n").grouped(8192)
    val result = CsvParser.parse[List](input, options)
    ZIO.attempt(Chunk.fromIterator(result)).refineOrDie { case e: CsvParser.Error => e }
  }

  override protected def parseLine(line: String, options: CsvParser.Options) = {
    CsvParser.parseLine[List](line, options)
  }

  override protected def splitStrings(strings: List[String], state: CsvParser.State) = CsvParser.splitStrings(strings, state)

  override protected def initialState = CsvParser.State.initial
  override protected def stateLeftover(s: CsvParser.State) = s.leftover
}

trait CsvParserParserSuite { self: ZIOSpecDefault =>

  protected def parse(
    lines: Iterable[String],
    options: CsvParser.Options,
  ): ZIO[Any, CsvParser.Error, Chunk[List[String]]]

  protected def parserSuite = suite("parser")(
    test("lots") {
      def line(i: Int) = List("basic string", " \"quoted \nstring\" ", i.toString, "456.789", "true").mkString(",")

      val lines = (1 to 10).map(line(_))
      parse(lines, CsvParser.Options.Defaults).map { result =>
        assertTrue(result.length == 10)
      }
    },
    test("empty input") {
      parse(List.empty[String], CsvParser.Options.Defaults).map { result =>
        assertTrue(result == Chunk.empty)
      }
    },
    test("single field / no delimiter") {
      parse(List("hello"), CsvParser.Options.Defaults).map { result =>
        assertTrue(result == Chunk(List("hello")))
      }
    },
    suite("malformed input")(
      test("unterminated quoted field at EOF is retained") {
        parse(List("a,\"b,c"), CsvParser.Options.Defaults).map { result =>
          assertTrue(result == Chunk(List("a", "\"b,c")))
        }
      },
    ),
    suite("maximum line length")(
      test("oversized line") {
        val options = CsvParser.Options.Defaults.copy(maximumLineLength = 3)
        parse(List("abcd", "ok"), options).either.map { result =>
          assertTrue(result == Left(CsvParser.Error.LineTooLong(3)))
        }
      },
      test("oversized state") {
        val options = CsvParser.Options.Defaults.copy(maximumLineLength = 3)
        parse(List("abcd"), options).either.map { result =>
          assertTrue(result == Left(CsvParser.Error.LineTooLong(3)))
        }
      },
    ),
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
      } ::
      test("does not skip quoted comment marker") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"))
        parse(List("""a,b,c""", """"#",value""", """#ignored"""), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List("#", "value"),
          ))
        }
      } ::
      test("trim interaction with comment prefix") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"), trim = CsvParser.Options.Trim.True)
        parse(List("a,b", "  # comment", "c,d"), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b"),
            List("c", "d"),
          ))
        }
      } ::
      test("no trimming does not skip indented comment") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"), trim = CsvParser.Options.Trim.False)
        parse(List("a,b", "  # comment", "c,d"), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b"),
            List("  # comment"),
            List("c", "d"),
          ))
        }
      } ::
      test("non-# comment prefix") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("//"))
        parse(List("a,b,c", "//ignored", "#not-a-comment", "d,e,f"), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List("#not-a-comment"),
            List("d", "e", "f"),
          ))
        }
      } ::
      test("empty prefix disables comments") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some(""))
        parse(List("#not-a-comment"), opts).map { result =>
          assertTrue(result == Chunk(List("#not-a-comment")))
        }
      } ::
      test("start trimming skips indented comment") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"), trim = CsvParser.Options.Trim.Start)
        parse(List("a,b", "  # comment", "c,d"), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b"),
            List("c", "d"),
          ))
        }
      } ::
      test("end trimming does not skip indented comment") {
        val opts = CsvParser.Options.Defaults.copy(commentPrefix = Some("#"), trim = CsvParser.Options.Trim.End)
        parse(List("a,b", "  # comment  ", "c,d"), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b"),
            List("  # comment"),
            List("c", "d"),
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
      test("does not skip row with blank first field") {
        val opts = CsvParser.Options.Defaults.copy(skipBlankRows = true)
        parse(List("a,b,c", ",value", "", "d,e,f"), opts).map { result =>
          assertTrue(result == Chunk(
            List("a", "b", "c"),
            List("", "value"),
            List("d", "e", "f"),
          ))
        }
      } ::
      test("no trimming retains whitespace-only rows") {
        val opts = CsvParser.Options.Defaults.copy(skipBlankRows = true, trim = CsvParser.Options.Trim.False)
        parse(List(" \t "), opts).map { result =>
          assertTrue(result == Chunk(List(" \t ")))
        }
      } ::
      test("start and end trimming skip whitespace-only rows") {
        val start = CsvParser.Options.Defaults.copy(skipBlankRows = true, trim = CsvParser.Options.Trim.Start)
        val end = CsvParser.Options.Defaults.copy(skipBlankRows = true, trim = CsvParser.Options.Trim.End)
        for {
          startResult <- parse(List(" \t "), start)
          endResult <- parse(List(" \t "), end)
        } yield {
          assertTrue(
            startResult == Chunk.empty,
            endResult == Chunk.empty,
          )
        }
      } ::
      test("does not skip quoted empty field or delimiter-only row") {
        val opts = CsvParser.Options.Defaults.copy(skipBlankRows = true)
        parse(List("\"\"", ","), opts).map { result =>
          assertTrue(result == Chunk(
            List(""),
            List("", ""),
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
    suite("trailing delimiter")(
      test("produces empty last field") {
        parse(List("a,b,"), CsvParser.Options.Defaults).map { result =>
          assertTrue(result == Chunk(List("a", "b", "")))
        }
      },
    ),
    suite("lines with only delimiters")(
      test("comma only") {
        parse(List(","), CsvParser.Options.Defaults).map { result =>
          assertTrue(result == Chunk(List("", "")))
        }
      },
      test("multiple delimiters") {
        parse(List(",,,,"), CsvParser.Options.Defaults).map { result =>
          assertTrue(result == Chunk(List("", "", "", "", "")))
        }
      },
    ),
  )
}

trait CsvSplitStringsSuite[S] { self: ZIOSpecDefault =>

  protected def splitStrings(strings: List[String], state: S): (S, List[String])

  protected def initialState: S
  protected def stateLeftover(s: S): String

  protected def splitStringsSuite = {
    suite("split strings")(
      test("CRLF split across chunks") {
        val (state, lines) = splitStrings(List("a,b\r", "\nc,d"), initialState)
        assertTrue(lines == List("a,b")) &&
        assertTrue(stateLeftover(state) == "c,d")
      },
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
          stateLeftover(state) == "fg\"",
        ) &&
        assertTrue(
          lines2 == List("fg\"\n\"\"\""),
          stateLeftover(state2) == "\"hi\"\"",
        ) &&
        assertTrue(
          lines3 == List("\"hi\"\"j\""),
          stateLeftover(state3) == "mno",
        )
      },
      test("trailing double quotes aligned to vector boundary") {
        val strings = List(
          "\"012345678901234567890123456789012345678901234567890123456789ab\"",
          "\n\"012345678901234567890123456789012345678901234567890123456789\n\"",
          "\"\n012345678901234567890123456789012345678901234567890123456789a\"",
          "\n\"012345678901234567890123456789012345678901234567890123456789\"\"",
          "\n0123456789\"\nmno",
        )
        val (state, lines) = splitStrings(strings, initialState)
        assertTrue(lines == List(
          "\"012345678901234567890123456789012345678901234567890123456789ab\"",
          "\"012345678901234567890123456789012345678901234567890123456789\n\"\"\n012345678901234567890123456789012345678901234567890123456789a\"",
          "\"012345678901234567890123456789012345678901234567890123456789\"\"\n0123456789\"",
        )) &&
        assertTrue(stateLeftover(state) == "mno")
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
      test("new line at vector boundary inside quoted line") {
        val value = "x" * 63
        val (state, lines) = splitStrings(List("\"" + value + "\nclose\"\nnext"), initialState)
        assertTrue(lines == List("\"" + value + "\nclose\"")) &&
        assertTrue(stateLeftover(state) == "next")
      },
      test("chunk size") {
        val csv = "a,\"b\nc\",\"d\"\"e\"\r\nf,g\nlast,row"
        check(Gen.int(1, csv.length)) { chunkSize =>
          val (state, lines) = splitStrings(csv.grouped(chunkSize).toList, initialState)
          assertTrue(lines == List("a,\"b\nc\",\"d\"\"e\"", "f,g")) &&
          assertTrue(stateLeftover(state) == "last,row")
        }
      },
      test("unterminated quote across chunks") {
        val (state, lines) = splitStrings(List("a,\"b", "\nc\"\n"), initialState)
        assertTrue(lines == List("a,\"b\nc\"")) &&
        assertTrue(stateLeftover(state) == "")
      },
      test("odd and even trailing quote runs across chunks") {
        val (oddState, oddLines) = splitStrings(List("a,\"b\"\"", "\"\nc,d\n"), initialState)
        val (evenState, evenLines) = splitStrings(List("a,\"b\"", "\"\nc,d"), initialState)
        assertTrue(
          oddLines == List("a,\"b\"\"\"", "c,d"),
          stateLeftover(oddState) == "",
        ) && assertTrue(
          evenLines == Nil,
          stateLeftover(evenState) == "a,\"b\"\"\nc,d",
        )
      },
      test("empty strings in input are skipped") {
        val (state, lines) = splitStrings(List("a,b\n", "", "c,d\n"), initialState)
        assertTrue(lines == List("a,b", "c,d")) &&
        assertTrue(stateLeftover(state) == "")
      },
      test("standalone \\r not treated as line separator") {
        val (state, lines) = splitStrings(List("a,b\r", "c,d"), initialState)
        assertTrue(lines == Nil) &&
        assertTrue(stateLeftover(state) == "a,b\rc,d")
      },
    )
  }
}

trait CsvParserLineSuite { self: ZIOSpecDefault =>

  protected def parseLine(line: String, options: CsvParser.Options): List[String]

  protected def parseLineSuite = {
    import CsvParser.Options

    suite("parse line")(
      test("empty line") {
        assertTrue(parseLine("", Options.Defaults) == List(""))
      },
      test("line starting with a quote") {
        val line = """"hello",world"""
        assertTrue(parseLine(line, Options.Defaults) == List("hello", "world"))
      },
      test("consecutive delimiters") {
        assertTrue(parseLine(",,", Options.Defaults) == List("", "", ""))
      },
      test("all fields quoted") {
        val line = """"a","b","c""""
        assertTrue(parseLine(line, Options.Defaults) == List("a", "b", "c"))
      },
      test("field that is just escaped quotes") {
        val line = "a,\"\"\"\",b"
        assertTrue(parseLine(line, Options.Defaults) == List("a", "\"", "b"))
      },
      suite("malformed input")(
        test("quote in unquoted field suppresses following delimiter") {
          assertTrue(parseLine("a,b\"c,d", Options.Defaults) == List("a", "b\"c,d"))
        },
        test("characters after closing quote preserve the quotes") {
          assertTrue(parseLine("a,\"b\"x,c", Options.Defaults) == List("a", "\"b\"x", "c"))
        },
        test("lone quote is preserved") {
          assertTrue(parseLine("\"", Options.Defaults) == List("\""))
        },
      ),
      suite("escape character")(
        test("double quote") {
          val line = """a,"b""c",d,e"f"""
          assertTrue(parseLine(line, Options.Defaults) == List("a", """b"c""", "d", "e\"f"))
        },
        test("preserves doubled quotes in unquoted fields") {
          assertTrue(parseLine("""a""b,c""", Options.Defaults) == List("""a""b""", "c"))
        },
      ),
      suite("delimiter")(
        test("comma") {
          val line = "abc,123,data,,"
          val result = parseLine(line, Options.Defaults.copy(delimiter = Options.Delimiter.Comma))
          assertTrue(result == List("abc", "123", "data", "", ""))
        },
        test("comma at vector boundary inside quoted field") {
          val value = "x" * 63
          val line = s""""$value,inside",tail"""
          val result = parseLine(line, Options.Defaults.copy(delimiter = Options.Delimiter.Comma))
          assertTrue(result == List(s"$value,inside", "tail"))
        },
        test("tab") {
          val line = "a,b\t\"c\td\"\te,f\t\t"
          val result = parseLine(line, Options.Defaults.copy(delimiter = Options.Delimiter.Tab))
          assertTrue(result == List("a,b", "c\td", "e,f", "", ""))
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
      test("quoted") {
        check(Gen.stringBounded(0, 10)(Gen.asciiChar)) { str =>
          val line = s"\"${str.replace("\"", "\"\"")}\""
          assertTrue(parseLine(line, Options.Defaults) == List(str))
        }
      },
      test("very long quoted field") {
        val longValue = "x" * 5000
        val line = s""""$longValue""""
        val result = parseLine(line, Options.Defaults)
        assertTrue(result == List(longValue))
      },
    )
  }
}
