package ceesvee.tests

import cats.effect.IO
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder
import ceesvee.fs2.Fs2CsvReader
import ceesvee.tests.model.NZGreenhouseGasEmissions
import ceesvee.tests.model.UkCausewayCoast
import ceesvee.tests.model.UkPropertySalesPricePaid
import ceesvee.zio.ZioCsvReader
import zio.ZIO
import zio.durationInt
import zio.stream.ZPipeline
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.net.URI
import java.nio.file.Paths

object RealWorldCsvSpec extends ZIOSpecDefault {
  import RealWorldFileHelper.*

  private val options = CsvReader.Options.Defaults

  override val spec = suite("RealWorldCsv")(
    suite("UK Causeway Coast")({
      val path = Paths.get(getClass.getResource("/csv/uk-causeway-coast-and-glens.csv").getPath)

      val expected = UkCausewayCoast(
        x = BigDecimal("677156.193266336"),
        y = BigDecimal("934878.749389788"),
        name = "Hezlett House",
        address = "107 Sea Road, Castlerock, Co. Londonderry",
        town = "Castlerock",
        postcode = "BT51 4TW",
        website = URI.create("http://www.discovernorthernireland.com/Hezlett-House-Castlerock-Coleraine-P8295"),
      )

      def assertResult(result: Seq[Either[CsvHeader.Errors, UkCausewayCoast]]) = {
        assertTrue(result.count(_.isRight) == 19) &&
        assertTrue(result.apply(6) == Right(expected))
      }

      List(
        test("scala") {
          readFile(path) { input =>
            val result = CsvReader.decodeWithHeader(input, UkCausewayCoast.csvHeader, options)
            result match {
              case l @ Left(_) => assertTrue(l.isRight)
              case Right(result) => assertResult(result.toSeq)
            }
          }
        },
        test("fs2") {
          val io = readFileFs2(path).through {
            Fs2CsvReader.decodeWithHeader(UkCausewayCoast.csvHeader, options)
          }.compile.toList
          catsIoToZio(io).map { result =>
            assertResult(result)
          }
        },
        test("zio") {
          val stream = readFileZio(path)
          ZioCsvReader.decodeWithHeader(stream, UkCausewayCoast.csvHeader, options)
            .runCollect
            .map { result =>
              assertResult(result)
            }
        },
      )
    }*),
    suite("NZ greenhouse gas emissions 2019")({
      val total = 19087L
      assertHeaderTotal("nz-greenhouse-gas-emissions-2019.csv", NZGreenhouseGasEmissions.csvHeader, total)
    }*),
    suite("UK property sales 2019")({
      val total = 1011675L
      assertTotal("uk-property-sales-price-paid-2019.csv", UkPropertySalesPricePaid.decoder, total)
    }*),
  ) @@ TestAspect.timeout(60.seconds) @@ TestAspect.timed

  private def assertTotal[T](fileName: String, decoder: CsvRecordDecoder[T], total: Long) = {
    val path = Paths.get(getClass.getResource(s"/csv/$fileName").getPath)

    List(
      test("scala") {
        val result = readFile(path) { input =>
          CsvReader.decode(input, options)(decoder).count(_.isRight).toLong
        }
        assertTrue(result == total)
      },
      test("fs2") {
        val io = readFileFs2(path).through {
          Fs2CsvReader.decode[IO, T](options)(implicitly, decoder)
        }.collect { case Right(v) => v }.compile.count
        catsIoToZio(io).map { count =>
          assertTrue(count == total)
        }
      },
      test("zio") {
        val pipeline = ZioCsvReader.decode(options)(decoder, implicitly).mapError {
          case e: CsvParser.Error.LineTooLong => e
        }.andThen(ZPipeline.mapZIO(ZIO.fromEither(_)))
        readFileZio(path).via(pipeline).runCount.map { count =>
          assertTrue(count == total)
        }
      },
    )
  }

  private def assertHeaderTotal[T](fileName: String, header: CsvHeader[T], total: Long) = {
    val path = Paths.get(getClass.getResource(s"/csv/$fileName").getPath)

    List(
      test("scala") {
        val result = readFile(path) { input =>
          CsvReader.decodeWithHeader(input, header, options)
            .map(_.count(_.isRight).toLong)
        }
        assertTrue(result == Right(total))
      },
      test("fs2") {
        val io = readFileFs2(path).through {
          Fs2CsvReader.decodeWithHeader(header, options)
        }.collect { case Right(v) => v }.compile.count
        catsIoToZio(io).map { count =>
          assertTrue(count == total)
        }
      },
      test("zio") {
        val stream = readFileZio(path)
        ZioCsvReader.decodeWithHeader(stream, header, options)
          .collectRight
          .runCount
          .map { count =>
            assertTrue(count == total)
          }
      },
    )
  }
}
