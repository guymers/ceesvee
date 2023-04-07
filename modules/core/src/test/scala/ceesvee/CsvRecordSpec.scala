package ceesvee

import cats.syntax.apply.*
import ceesvee.test.illTyped
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes
import zio.test.assertTrue

import scala.collection.immutable.SortedMap

object CsvRecordSpec extends ZIOSpecDefault {

  override val spec = suite("CsvRecord")(
    test("valid") {
      val t = Test("a str", None, 123, 123.45f, true, Some(5))
      val _ = Test.decoder
      val encoded = CsvRecordEncoder[Test].encode(t)
      val decoded = CsvRecordDecoder[Test].decode(encoded)
      assertTrue(decoded == Right(t))
    },
    test("compose") {
      val t = Test("a str", None, 123, 123.45f, true, Some(5))
      val tce = TestCustomError("another string", 456)
      val fields = Vector("a str", "", "123", "123.45", "true", "5", "another string", "456")

      val encoder = (Test.encoder, TestCustomError.encoder).tupled
      val encoded = encoder.encode((t, tce))

      val decoder = (Test.decoder, TestCustomError.decoder).tupled
      val decoded = decoder.decode(encoded)

      val decoderT = Test.decoder <* TestCustomError.decoder
      val decodedT = decoderT.decode(encoded)
      val decoderTCE = Test.decoder *> TestCustomError.decoder
      val decodedTCE = decoderTCE.decode(encoded)

      assertTrue(encoded == fields) &&
      assertTrue(decoded == Right((t, tce))) &&
      assertTrue(decodedT == Right(t)) &&
      assertTrue(decodedTCE == Right(tce))
    },
    test("not enough fields") {
      val fields = Vector("a str", "", "123", "123.45", "true")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Errors(
        raw = fields,
        errors = SortedMap(
          5 -> CsvRecordDecoder.Errors.Field.Missing,
        ),
      )))
    },
    test("invalid fields") {
      val fields = Vector("a str", "", "not int", "123.45", "not bool", "")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Errors(
        raw = fields,
        errors = SortedMap(
          2 -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error(
            "not int",
            "invalid numeric value, required int",
          )),
          4 -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error(
            "not bool",
            "invalid boolean value valid values are 't','true','y','yes' and 'f','false','n','no'",
          )),
        ),
      )))
    },
    suite("custom error")(
      test("simple") {
        val fields = Vector("a str", "0")
        val result = CsvRecordDecoder[TestCustomError].decode(fields)
        assertTrue(result == Left(CsvRecordDecoder.Errors(
          raw = fields,
          errors = SortedMap(
            0 -> CsvRecordDecoder.Errors.Record("int cannot be 0"),
          ),
        )))
      },
      test("nested") {
        val fields = Vector("str", "a str", "0", "2")
        val result = CsvRecordDecoder[TestCustomErrorNested].decode(fields)
        assertTrue(result == Left(CsvRecordDecoder.Errors(
          raw = fields,
          errors = SortedMap(
            1 -> CsvRecordDecoder.Errors.Record("int cannot be 0"),
          ),
        )))
      },
    ),
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
        assertTrue(result == Left(CsvRecordDecoder.Errors(
          raw = fields,
          errors = SortedMap(
            3 -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error("", "invalid numeric value, required int")),
            4 -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error("", "invalid numeric value, required float")),
            5 -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error(
              "",
              "invalid boolean value valid values are 't','true','y','yes' and 'f','false','n','no'",
            )),
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
    suite("derive")(
      test("optional optional field") {
        illTyped(""" CsvRecordDecoder[Option[Option[String]]] """)
        illTyped(""" CsvRecordEncoder[Option[Option[String]]] """)
        assertCompletes
      },
      test("optional optional case class") {
        illTyped(""" CsvRecordDecoder[Option[Option[Test]]] """)
        illTyped(""" CsvRecordEncoder[Option[Option[Test]]] """)
        assertCompletes
      },
    ),
  )
}

case class TestCustomError(
  str: String,
  int: Int,
)
object TestCustomError {
  implicit val decoder: CsvRecordDecoder[TestCustomError] = CsvRecordDecoder.derived[TestCustomError].emap { v =>
    if (v.int == 0) Left("int cannot be 0")
    else Right(v)
  }
  implicit val encoder: CsvRecordEncoder[TestCustomError] = CsvRecordEncoder.derived
}

case class TestCustomErrorNested(
  str: String,
  inner: TestCustomError,
  int: Int,
)
object TestCustomErrorNested {
  implicit val decoder: CsvRecordDecoder[TestCustomErrorNested] = CsvRecordDecoder.derived
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
