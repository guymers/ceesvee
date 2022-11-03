package ceesvee

import zio.test.*

import scala.collection.immutable.SortedMap

object CsvRecordSpec extends ZIOSpecDefault {

  override val spec = suite("CsvRecord")(
    test("valid") {
      val t = Test("a str", None, 123, 123.45f, true, Some(5))
      val encoded = CsvRecordEncoder[Test].encode(t)
      val decoded = CsvRecordDecoder[Test].decode(encoded)
      assertTrue(decoded == Right(t))
    },
    test("not enough fields") {
      val fields = List("a str", "", "123", "123.45", "true")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Error(
        raw = fields,
        errors = SortedMap(5 -> CsvRecordDecoder.Error.Field.Missing),
      )))
    },
    test("invalid fields") {
      val fields = List("a str", "", "not int", "123.45", "not bool", "")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Error(
        raw = fields,
        errors = SortedMap(
          2 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("not int", "invalid int value")),
          4 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("not bool", "invalid boolean value")),
        ),
      )))
    },
  )
}
