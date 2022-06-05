package ceesvee

import zio.duration.*
import zio.test.*

import scala.collection.immutable.SortedMap

object CsvRecordDecoderSpec extends DefaultRunnableSpec {

  override val spec = suite("CsvRecordDecoder")(
    test("valid") {
      val fields = List("a str", "123", "123.45", "true", "5")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Right(Test("a str", 123, 123.45f, true, Some(5))))
    },
    test("not enough fields") {
      val fields = List("a str", "123", "123.45", "true")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Error(
        raw = fields,
        errors = SortedMap(4 -> CsvRecordDecoder.Error.Field.Missing),
      )))
    },
    test("invalid fields") {
      val fields = List("a str", "not int", "123.45", "not bool", "")
      val result = CsvRecordDecoder[Test].decode(fields)
      assertTrue(result == Left(CsvRecordDecoder.Error(
        raw = fields,
        errors = SortedMap(
          1 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("not int", "invalid int value")),
          3 -> CsvRecordDecoder.Error.Field.Invalid(CsvFieldDecoder.Error("not bool", "invalid boolean value")),
        ),
      )))
    },
  )

  override val aspects = List(
    TestAspect.timeout(15.seconds),
  )
}

case class Test(
  str: String,
  int: Int,
  float: Float,
  bool: Boolean,
  optInt: Option[Int],
)
object Test {
  implicit val decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derive
}
