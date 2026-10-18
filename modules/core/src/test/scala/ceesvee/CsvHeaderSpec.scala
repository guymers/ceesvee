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
