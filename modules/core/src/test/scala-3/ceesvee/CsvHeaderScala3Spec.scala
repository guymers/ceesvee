package ceesvee

import ceesvee.test.illTyped
import zio.test.ZIOSpecDefault
import zio.test.assertCompletes
import zio.test.assertTrue

import scala.util.Try

object CsvHeaderScala3Spec extends ZIOSpecDefault {
  import CsvHeaderSpec.Test

  override val spec = suite("CsvHeader")(
    suite("from tuple")(
      test("an empty tuple is a compile error") {
        illTyped(""" CsvHeader.createFromTuple(EmptyTuple)(using Test.decoder) """)
        assertCompletes
      },
      test("a tuple with a non-String element is a compile error") {
        illTyped(""" CsvHeader.createFromTuple(("str", 1))(using Test.decoder) """)
        assertCompletes
      },
      test("valid") {
        val header = CsvHeader.createFromTuple(("a", "b", "c"))(using Test.decoder)
        val decoder = header.create(List("a", "b", "c")).toOption.get

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
}
