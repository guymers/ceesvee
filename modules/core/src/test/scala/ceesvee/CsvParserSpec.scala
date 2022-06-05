package ceesvee

import zio.duration.*
import zio.test.*

object CsvParserSpec extends DefaultRunnableSpec {

  override val spec = suite("CsvParser")(
    suite("parse")(
      test("lots") {
        def line(i: Int) = List("basic string", " \"quoted \nstring\" ", i.toString, "456.789", "true").mkString(",")

        val lines = (1 to 10).map(line(_)).mkString("\n")
        val input = lines.grouped(8192)
        val result = CsvParser.parse[List](input, CsvParser.Options(maximumLineLength = 1000))
        assertTrue(result.toList.length == 10)
      },
    ),
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
    suite("parse line")(
      test("line") {
        val line = "abc, def ,,\" g,\"\"h\"\",\ti\" , "
        val result = CsvParser.parseLine[List](line)
        assertTrue(result == List("abc", "def", "", " g,\"h\",\ti", ""))
      },
    ),
  )

  override val aspects = List(
    TestAspect.timeout(15.seconds),
  )
}
