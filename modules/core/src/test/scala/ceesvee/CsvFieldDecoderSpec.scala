package ceesvee

import zio.duration.*
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

object CsvFieldDecoderSpec extends DefaultRunnableSpec {
  import CsvFieldDecoder.Error

  override val spec = suite("CsvFieldDecoder")(
    testM("string") {
      check(Gen.anyString) { s =>
        val result = CsvFieldDecoder[String].decode(s)
        assertTrue(result == Right(s))
      }
    },
    suite("boolean")({
      val valid = Map(
        "t" -> true,
        "true" -> true,
        "f" -> false,
        "false" -> false,
      )

      List(
        test("valid") {
          valid.foldLeft(assertCompletes) { case (a, (str, bool)) =>
            a && assertTrue(CsvFieldDecoder[Boolean].decode(str) == Right(bool))
          }
        },
        testM("invalid") {
          check(Gen.anyString.filter(s => !valid.contains(s))) { s =>
            val result = CsvFieldDecoder[Boolean].decode(s)
            assertTrue(result == Left(Error(s, "invalid boolean value")))
          }
        },
      )
    }*),
    suite("numbers")(
      suite("int")(
        testM("valid") {
          check(Gen.anyInt) { i =>
            val result = CsvFieldDecoder[Int].decode(i.toString)
            assertTrue(result == Right(i))
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Int].decode("123.45")
          assertTrue(result == Left(Error("123.45", "invalid int value")))
        },
      ),
      suite("long")(
        testM("valid") {
          check(Gen.anyLong) { l =>
            val result = CsvFieldDecoder[Long].decode(l.toString)
            assertTrue(result == Right(l))
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Long].decode("123.45")
          assertTrue(result == Left(Error("123.45", "invalid long value")))
        },
      ),
      suite("float")(
        testM("valid") {
          check(Gen.anyFloat) { f =>
            val result = CsvFieldDecoder[Float].decode(f.toString)
            assertTrue(result == Right(f))
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[Float].decode("_123.45")
          assertTrue(result == Left(Error("_123.45", "invalid float value")))
        },
      ),
      suite("double")(
        testM("valid") {
          check(Gen.anyDouble) { f =>
            val result = CsvFieldDecoder[Double].decode(f.toString)
            assertTrue(result == Right(f))
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
        testM("valid") {
          check(Gen.anyLocalDate) { f =>
            val result = CsvFieldDecoder[LocalDate].decode(f.toString)
            assertTrue(result == Right(f))
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
        testM("valid") {
          check(Gen.anyLocalDateTime) { f =>
            val result = CsvFieldDecoder[LocalDateTime].decode(f.toString)
            assertTrue(result == Right(f))
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
        testM("valid") {
          check(Gen.anyLocalTime) { f =>
            val result = CsvFieldDecoder[LocalTime].decode(f.toString)
            assertTrue(result == Right(f))
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
        testM("valid") {
          check(Gen.anyInstant) { f =>
            val result = CsvFieldDecoder[Instant].decode(f.toString)
            assertTrue(result == Right(f))
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
        testM("valid") {
          check(Gen.anyOffsetDateTime) { f =>
            val result = CsvFieldDecoder[OffsetDateTime].decode(f.toString)
            assertTrue(result == Right(f))
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[OffsetDateTime].decode("2000-01-01T00:00:00+50:00")
          assertTrue(result.isLeft) // msg differs between Java versions
        },
      ),
      suite("zoned date time")(
        testM("valid") {
          check(Gen.anyZonedDateTime) { f =>
            val result = CsvFieldDecoder[ZonedDateTime].decode(f.toString)
            assertTrue(result == Right(f))
          }
        },
        test("invalid") {
          println(ZonedDateTime.now().toString)
          val result = CsvFieldDecoder[ZonedDateTime].decode("2000-01-01T00:00:00+00:00[TimeZone]")
          assertTrue(result == Left(Error(
            "2000-01-01T00:00:00+00:00[TimeZone]",
            "Text '2000-01-01T00:00:00+00:00[TimeZone]' could not be parsed, unparsed text found at index 25",
          )))
        },
      ),
      suite("zone")(
        testM("valid") {
          check(Gen.anyZoneId) { f =>
            val result = CsvFieldDecoder[ZoneId].decode(f.toString)
            assertTrue(result == Right(f))
          }
        },
        test("invalid") {
          val result = CsvFieldDecoder[ZoneId].decode("A/B")
          assertTrue(result == Left(Error("A/B", "Unknown time-zone ID: A/B")))
        },
      ),
    ),
    suite("uuid")(
      testM("valid") {
        check(Gen.anyUUID) { f =>
          val result = CsvFieldDecoder[UUID].decode(f.toString)
          assertTrue(result == Right(f))
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
          a && assertTrue(CsvFieldDecoder[URI].decode(uri.toString) == Right(uri))
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

  override val aspects = List(
    TestAspect.timeout(15.seconds),
  )
}
