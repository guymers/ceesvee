package ceesvee

import zio.test.*

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

object CsvFieldSpec extends ZIOSpecDefault {
  import CsvFieldDecoder.Error

  override val spec = suite("CsvField")(
    test("string") {
      check(Gen.string) { s =>
        assertGolden(s)
      }
    },
    suite("boolean")({
      val valid = Map(
        // true
        "t" -> true,
        "true" -> true,
        "TRUE" -> true,
        "yes" -> true,
        "y" -> true,
        // false
        "f" -> false,
        "false" -> false,
        "FALSE" -> false,
        "no" -> false,
        "n" -> false,
      )

      List(
        test("encode") {
          assertTrue(CsvFieldEncoder[Boolean].encode(false) == "false") &&
          assertTrue(CsvFieldEncoder[Boolean].encode(true) == "true")
        },
        test("decode") {
          valid.foldLeft(assertCompletes) { case (a, (str, bool)) =>
            a && assertTrue(CsvFieldDecoder[Boolean].decode(str) == Right(bool))
          }
        },
        test("valid") {
          check(Gen.boolean) { b =>
            assertGolden(b)
          }
        },
        test("invalid") {
          check(Gen.string.filter(s => !valid.contains(s))) { s =>
            val result = CsvFieldDecoder[Boolean].decode(s)
            assertTrue(result == Left(Error(s, "invalid boolean value")))
          }
        },
      )
    }*),
    suite("numbers")(
      suite("int")(
        test("valid") {
          check(Gen.int) { i =>
            assertGolden(i)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Int].decode("123.45")
          assertTrue(result == Left(Error("123.45", "invalid int value")))
        },
      ),
      suite("long")(
        test("valid") {
          check(Gen.long) { l =>
            assertGolden(l)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Long].decode("123.45")
          assertTrue(result == Left(Error("123.45", "invalid long value")))
        },
      ),
      suite("float")(
        test("valid") {
          check(Gen.float) { f =>
            assertGolden(f)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Float].decode("_123.45")
          assertTrue(result == Left(Error("_123.45", "invalid float value")))
        },
      ),
      suite("double")(
        test("valid") {
          check(Gen.double) { d =>
            assertGolden(d)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Double].decode("_123.45")
          assertTrue(result == Left(Error("_123.45", "invalid double value")))
        },
      ),
      suite("big decimal")(
        test("valid") {
          check(Gen.bigDecimal(Double.MinValue, Double.MaxValue)) { d =>
            assertGolden(d)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Double].decode("_123.45")
          assertTrue(result == Left(Error("_123.45", "invalid double value")))
        },
      ),
    ),
    suite("times")(
      suite("date")(
        test("valid") {
          check(Gen.localDate) { date =>
            assertGolden(date)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[LocalDate].decode("2000-00-01")
          assertTrue(result == Left(Error(
            "2000-00-01",
            "Invalid value for MonthOfYear (valid values 1 - 12): 0",
          )))
        },
      ),
      suite("date time")(
        test("valid") {
          check(Gen.localDateTime) { dateTime =>
            assertGolden(dateTime)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[LocalDateTime].decode("2000-01-01T00:00:99")
          assertTrue(result == Left(Error(
            "2000-01-01T00:00:99",
            "Invalid value for SecondOfMinute (valid values 0 - 59): 99",
          )))
        },
      ),
      suite("time")(
        test("valid") {
          check(Gen.localTime) { time =>
            assertGolden(time)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[LocalTime].decode("25:00:00")
          assertTrue(result == Left(Error(
            "25:00:00",
            "Invalid value for HourOfDay (valid values 0 - 23): 25",
          )))
        },
      ),
      suite("instant")(
        test("valid") {
          check(Gen.instant) { instant =>
            assertGolden(instant)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Instant].decode("2000-01-01T00:99:00Z")
          assertTrue(result == Left(Error(
            "2000-01-01T00:99:00Z",
            "Text '2000-01-01T00:99:00Z' could not be parsed at index 0",
          )))
        },
      ),
      suite("offset date time")(
        test("valid") {
          check(Gen.offsetDateTime) { dateTime =>
            assertGolden(dateTime)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[OffsetDateTime].decode("2000-01-01T00:00:00+50:00")
          assertTrue(result.isLeft) // msg differs between Java versions
        },
      ),
      suite("zoned date time")(
        test("valid") {
          check(Gen.zonedDateTime) { dateTime =>
            assertGolden(dateTime)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[ZonedDateTime].decode("2000-01-01T00:00:00+00:00[TimeZone]")
          assertTrue(result == Left(Error(
            "2000-01-01T00:00:00+00:00[TimeZone]",
            "Text '2000-01-01T00:00:00+00:00[TimeZone]' could not be parsed, unparsed text found at index 25",
          )))
        },
      ),
      suite("zone")(
        test("valid") {
          check(Gen.zoneId) { id =>
            assertGolden(id)
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[ZoneId].decode("A/B")
          assertTrue(result == Left(Error("A/B", "Unknown time-zone ID: A/B")))
        },
      ),
    ),
    suite("uuid")(
      test("valid") {
        check(Gen.uuid) { uuid =>
          assertGolden(uuid)
        }
      },
      test("invalid") {
        val result = CsvFieldDecoder[UUID].decode("3dd8b0fc-2a20-4e4d-8c41-f0042845fa9z")
        assertTrue(result.isLeft) // msg differs between Java versions
      },
    ),
    suite("uri")(
      test("valid") {
        val valid = List(
          new URI("https://example.com"),
          new URI("https://example.com/path"),
          new URI("https://example.com/path?query=q"),
        )
        valid.foldLeft(assertCompletes) { case (a, uri) =>
          a && assertGolden(uri)
        }
      },
      test("invalid") {
        val result = CsvFieldDecoder[URI].decode("https:\\example.com")
        assertTrue(result == Left(Error(
          "https:\\example.com",
          "Illegal character in opaque part at index 6: https:\\example.com",
        )))
      },
    ),
  )

  private def assertGolden[T: CsvFieldDecoder: CsvFieldEncoder](t: T) = {
    val encoded = CsvFieldEncoder[T].encode(t)
    val decoded = CsvFieldDecoder[T].decode(encoded)
    assertTrue(decoded == Right(t))
  }
}
