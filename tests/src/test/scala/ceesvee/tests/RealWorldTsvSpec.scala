package ceesvee.tests

import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.fs2.Fs2CsvReader
import ceesvee.tests.model.AustralianPostcodes
import ceesvee.zio.ZioCsvReader
import zio.durationInt
import zio.stream.ZPipeline
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object RealWorldTsvSpec extends ZIOSpecDefault {
  import RealWorldFileHelper.*

  private val options = CsvReader.Options.Defaults.copy(
    delimiter = CsvParser.Options.Delimiter.Tab,
  )

  override val spec = suite("RealWorldTsv")(
    suite("Australian postcodes")({
      val resource = "tsv/australian-postcodes.tsv"

      val expected = AustralianPostcodes(
        suburb = "PADDINGTON",
        state = "NSW",
        postcode = 2021,
      )

      def assertResult(result: Seq[Either[CsvHeader.Errors, AustralianPostcodes]]) = {
        assertTrue(result.count(_.isRight) == 16753) &&
        assertTrue(result.apply(11040) == Right(expected))
      }

      List(
        test("scala") {
          readResource(resource) { input =>
            val result = CsvReader.decodeWithHeader(input, AustralianPostcodes.csvHeader, options)
            result match {
              case l @ Left(_) => assertTrue(l.isRight)
              case Right(result) => assertResult(result.toSeq)
            }
          }
        },
        test("fs2") {
          val io = readResourceFs2(resource).through(fs2.text.utf8.decode).through {
            Fs2CsvReader.decodeWithHeader(AustralianPostcodes.csvHeader, options)
          }.compile.toList
          catsIoToZio(io).map { result =>
            assertResult(result)
          }
        },
        test("zio") {
          val stream = readResourceZio(resource).via(ZPipeline.utfDecode)
          ZioCsvReader.decodeWithHeader(stream, AustralianPostcodes.csvHeader, options)
            .runCollect
            .map { result =>
              assertResult(result)
            }
        },
      )
    }*),
  ) @@ TestAspect.timeout(60.seconds) @@ TestAspect.timed
}
