package ceesvee

import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object CsvReaderSpec extends ZIOSpecDefault {

  private val header = CsvHeader.create[Row](::("a", List("b", "c")))
  private val options = CsvReader.Options.Defaults

  override val spec = suite("CsvReader")(
    suite("decodeWithHeader")(
      test("missing header") {
        val result = CsvReader.decodeWithHeader(Iterator("a,b\ns,1"), header, options)

        assertTrue(result == Left(CsvHeader.MissingHeaders(::("c", Nil))))
      },
      test("empty input") {
        val result = CsvReader.decodeWithHeader(Iterator.empty[String], header, options)

        assertTrue(result == Left(CsvHeader.MissingHeaders(::("a", List("b", "c")))))
      },
      test("only header row") {
        val result = CsvReader.decodeWithHeader(Iterator("a,b,c"), header, options)

        assertTrue(result.map(_.toList) == Right(List.empty))
      },
      test("valid rows") {
        val result = CsvReader.decodeWithHeader(Iterator("a,b,c\ns,1,true"), header, options)

        assertTrue(result.map(_.toList) == Right(List(Right(Row("s", 1, true)))))
      },
      test("columns are reordered by header name") {
        val result = CsvReader.decodeWithHeader(Iterator("c,b,a\ntrue,1,s"), header, options)

        assertTrue(result.map(_.toList) == Right(List(Right(Row("s", 1, true)))))
      },
    ),
  )

  private case class Row(a: String, b: Int, c: Boolean) derives CsvRecordDecoder
}
