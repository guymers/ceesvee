package ceesvee

import zio.test.*

import scala.collection.immutable.SortedMap

object CsvRecordSpec extends ZIOSpecDefault {

  override val spec = suite("CsvRecord")(
    test("valid") {
      val t = Test("a str", None, 123, 123.45f, true, Some(5))
      val _ = Test.decoder
      val encoded = CsvRecordEncoder[Test].encode(t)
      val decoded = CsvRecordDecoder[Test].decode(encoded.toIndexedSeq)
      assertTrue(decoded == Right(t))
    },
    test("not enough fields") {
      val fields = Vector("a str", "", "123", "123.45", "true")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Error(
        raw = fields,
        errors = SortedMap(
          5 -> CsvRecordDecoder.Error.Field.Missing,
        ),
      )))
    },
    test("invalid fields") {
      val fields = Vector("a str", "", "not int", "123.45", "not bool", "")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Error(
        raw = fields,
        errors = SortedMap(
          2 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("not int", "invalid int value")),
          4 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("not bool", "invalid boolean value")),
        ),
      )))
    },
    suite("nested optional object")(
      test("not present") {
        val t = TestOptionalNestedObject("a str", None, 1, None)
        val fields = Vector("a str", "", "", "", "", "", "", "1", "")
        val encoded = CsvRecordEncoder[TestOptionalNestedObject].encode(t)
        val decoded = CsvRecordDecoder[TestOptionalNestedObject].decode(fields)
        assertTrue(encoded == fields) && assertTrue(decoded == Right(t))
      },
      test("one field present") {
        val fields = Vector("a str", "inner str", "", "", "", "", "", "1", "")
        val result = CsvRecordDecoder[TestOptionalNestedObject].decode(fields)
        assertTrue(result == Left(CsvRecordDecoder.Error(
          raw = fields,
          errors = SortedMap(
            3 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("", "invalid int value")),
            4 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("", "invalid float value")),
            5 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("", "invalid boolean value")),
          ),
        )))
      },
      test("present") {
        val t = TestOptionalNestedObject(
          str = "a str",
          nested = Some(Test("a str", None, 123, 123.45f, true, Some(5))),
          int = 1,
          intOpt = None,
        )
        val fields = Vector("a str", "a str", "", "123", "123.45", "true", "5", "1", "")
        val encoded = CsvRecordEncoder[TestOptionalNestedObject].encode(t)
        val decoded = CsvRecordDecoder[TestOptionalNestedObject].decode(fields)
        assertTrue(encoded == fields) && assertTrue(decoded == Right(t))
      },
    ),
  )
}

case class TestOptionalNestedObject(
  str: String,
  nested: Option[Test],
  int: Int,
  intOpt: Option[Int],
)
object TestOptionalNestedObject {
  implicit val decoder: CsvRecordDecoder[TestOptionalNestedObject] = CsvRecordDecoder.derived
  implicit val encoder: CsvRecordEncoder[TestOptionalNestedObject] = CsvRecordEncoder.derived
}
