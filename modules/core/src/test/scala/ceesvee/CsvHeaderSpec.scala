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

      List(
        test("valid row") {
          val row = List("", "str", "", "1", "", "true", "")
          val result = decoder.decode(row)
          assertTrue(result == Right(Test("str", 1, true)))
        },
        test("invalid row") {
          val row = List("", "str", "", "invalid", "", "true", "")
          val result = decoder.decode(row)
          assertTrue(result == Left(CsvRecordDecoder.Error(
            row,
            SortedMap(
              3 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("invalid", "invalid int value")),
            ),
          )))
        },
      )
    }*),
  )

  case class Test(
    a: String,
    b: Int,
    c: Boolean,
  )
  object Test {
    implicit val decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derive
  }
}
