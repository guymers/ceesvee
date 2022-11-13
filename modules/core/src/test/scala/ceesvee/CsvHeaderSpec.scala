package ceesvee

import zio.test.*

import scala.collection.immutable.SortedMap

object CsvHeaderSpec extends ZIOSpecDefault {

  private val header = CsvHeader.create(::("a", List("b", "c")))(Test.decoder)

  override val spec = suite("CsvHeader")(
    test("missing headers") {
      val result = header.create(List("b"))
      assertTrue(result == Left(CsvHeader.MissingHeaders(::("a", List("c")))))
    },
    suite("decodes")({
      val headers = List("_a", "a", "_b", "b", "_c", "c", "_d")
      val decoder = header.create(headers).toOption.get

      test("valid row") {
        val row = Vector("", "str", "", "1", "", "true", "")
        val map = decoder.withHeaders(row)
        val result = decoder.decode(row)
        assertTrue(map == Map("a" -> "str", "b" -> "1", "c" -> "true")) &&
        assertTrue(result == Right(Test("str", 1, true)))
      } ::
      test("invalid row") {
        val row = Vector("", "str", "", "invalid", "", "true", "")
        val map = decoder.withHeaders(row)
        val result = decoder.decode(row)
        assertTrue(map == Map("a" -> "str", "b" -> "invalid", "c" -> "true")) &&
        assertTrue(result == Left(CsvHeader.Error(
          map,
          SortedMap(
            "b" -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("invalid", "invalid int value")),
          ),
        )))
      } :: Nil
    }*),
  )

  case class Test(
    a: String,
    b: Int,
    c: Boolean,
  )
  object Test {
    implicit val decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derived
  }
}
