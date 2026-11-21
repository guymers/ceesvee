package ceesvee

import ceesvee.test.illTyped
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes
import zio.test.assertTrue

import scala.collection.immutable.SortedMap
import scala.util.Try

object CsvHeaderSpec extends ZIOSpecDefault {

  private val header = CsvHeader.create(::("a", List("b", "c")))(using Test.decoder)

  override val spec = suite("CsvHeader")(
    test("missing headers") {
      val result = header.create(List("b"))
      assertTrue(result == Left(CsvHeader.MissingHeaders(::("a", List("c")))))
    },
    suite("duplicate columns")(
      test("the first column wins") {
        val decoder = header.create(List("a", "a", "b", "c")).toOption.get
        val row = Vector("first", "second", "1", "true")

        assertTrue(decoder.withHeaders(row) == Map("a" -> "first", "b" -> "1", "c" -> "true")) &&
        assertTrue(decoder.decode(row) == Right(Test("first", 1, c = true)))
      },
      test("the first column wins when reordered") {
        val decoder = header.create(List("b", "a", "a", "c")).toOption.get
        val row = Vector("1", "first", "second", "true")

        assertTrue(decoder.withHeaders(row) == Map("a" -> "first", "b" -> "1", "c" -> "true")) &&
        assertTrue(decoder.decode(row) == Right(Test("first", 1, c = true)))
      },
      test("duplicate expected headers are rejected") {
        val result = Try(CsvHeader.create(::("a", List("a", "c")))(using Test.decoder))

        assertTrue(result.isFailure)
      },
    ),
    suite("decodes")({
      val headers = List("_a", "a", "_b", "b", "_c", "c", "_d")
      val decoder = header.create(headers).toOption.get

      test("valid row") {
        val row = Vector("", "str", "", "1", "", "true", "")
        val map = decoder.withHeaders(row)
        val result = decoder.decode(row)
        assertTrue(map == Map("a" -> "str", "b" -> "1", "c" -> "true")) &&
        assertTrue(result == Right(Test("str", 1, c = true)))
      } ::
      test("invalid row") {
        val row = Vector("", "str", "", "invalid", "", "true", "")
        val map = decoder.withHeaders(row)
        val result = decoder.decode(row)
        assertTrue(map == Map("a" -> "str", "b" -> "invalid", "c" -> "true")) &&
        assertTrue(result == Left(CsvHeader.Errors(
          map,
          SortedMap(
            "b" -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error(
              "invalid",
              "invalid numeric value, required int",
            )),
          ),
        )))
      } :: Nil
    }*),
    suite("record errors")(
      test("are reported against the record not the first column") {
        val recordDecoder = CsvRecordDecoder[Int].emap(i => if (i > 0) Right(i) else Left("must be positive"))
        val decoder = CsvHeader.create(::("a", Nil))(using recordDecoder).create(List("a")).toOption.get

        val result = decoder.decode(Vector("-1"))
        assertTrue(result == Left(CsvHeader.Errors(
          Map("a" -> "-1"),
          SortedMap(CsvHeader.Errors.RecordKey -> CsvRecordDecoder.Errors.Record("must be positive")),
        ))) &&
        assertTrue(result.swap.toOption.exists(_.getMessage.endsWith("because: record must be positive")))
      },
    ),
    suite("short rows")({
      val inOrder = header.create(List("a", "b", "c")).toOption.get
      val reordered = header.create(List("b", "a", "c")).toOption.get

      test("in header order") {
        val row = Vector("str", "1")
        assertTrue(inOrder.decode(row) == Left(CsvHeader.Errors(
          Map("a" -> "str", "b" -> "1"),
          SortedMap("c" -> CsvRecordDecoder.Errors.Field.Missing),
        )))
      } ::
      test("in a reordered header") {
        val row = Vector("1", "str")
        assertTrue(reordered.decode(row) == Left(CsvHeader.Errors(
          Map("a" -> "str", "b" -> "1"),
          SortedMap("c" -> CsvRecordDecoder.Errors.Field.Missing),
        )))
      } ::
      test("when the missing field is not last in the header order") {
        val decoder = header.create(List("b", "c", "a")).toOption.get
        val row = Vector("1", "true")
        assertTrue(decoder.decode(row) == Left(CsvHeader.Errors(
          Map("b" -> "1", "c" -> "true"),
          SortedMap("a" -> CsvRecordDecoder.Errors.Field.Missing),
        )))
      } ::
      test("reports missing and invalid fields together") {
        val row = Vector("nope", "str")
        assertTrue(reordered.decode(row) == Left(CsvHeader.Errors(
          Map("a" -> "str", "b" -> "nope"),
          SortedMap(
            "b" -> CsvRecordDecoder.Errors.Field.Invalid(CsvFieldDecoder.Error(
              "nope",
              "invalid numeric value, required int",
            )),
            "c" -> CsvRecordDecoder.Errors.Field.Missing,
          ),
        )))
      } :: Nil
    }*),
    suite("from tuple")(
      test("an empty tuple is a compile error") {
        illTyped(""" CsvHeader.createFromTuple(EmptyTuple)(using CsvRecordDecoder[Test]) """)
        assertCompletes
      },
      test("a tuple with a non-String element is a compile error") {
        illTyped(""" CsvHeader.createFromTuple(("str", 1))(using CsvRecordDecoder[Test]) """)
        assertCompletes
      },
      test("valid") {
        val tupleHeader = CsvHeader.createFromTuple(("a", "b", "c"))(using Test.decoder)
        val decoder = tupleHeader.create(List("a", "b", "c")).toOption.get

        val row = Vector("str", "1", "true")
        val map = decoder.withHeaders(row)
        val result = decoder.decode(row)
        assertTrue(map == Map("a" -> "str", "b" -> "1", "c" -> "true")) &&
        assertTrue(result == Right(Test("str", 1, c = true)))
      },
      test("wrong number of headers fails at runtime") {
        val result = Try(CsvHeader.createFromTuple(("str", "int"))(using Test.decoder))

        assertTrue(result.isFailure)
      },
    ),
  )

  case class Test(
    a: String,
    b: Int,
    c: Boolean,
  )
  object Test {
    given decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derived
  }
}
