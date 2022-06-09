package ceesvee.tests

import cats.effect.IO
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvParser.Error
import ceesvee.CsvRecordDecoder
import ceesvee.Fs2CsvParser
import ceesvee.ZioCsvParser
import ceesvee.tests.model.NZGreenhouseGasEmissions
import ceesvee.tests.model.UkCausewayCoast
import ceesvee.tests.model.UkPropertySalesPricePaid
import zio.ZIO
import zio.duration.*
import zio.stream.ZStream
import zio.stream.ZTransducer
import zio.test.*

import java.io.InputStream
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object RealWorldCsvSpec extends DefaultRunnableSpec {

  private val options = CsvParser.Options(
    maximumLineLength = 1000,
  )

  override val spec = suite("RealWorldCsv")(
    suite("UK Causeway Coast")({
      val path = Paths.get(getClass.getResource("/csv/uk-causeway-coast-and-glens.csv").getPath)

      val expected = UkCausewayCoast(
        x = BigDecimal("-6.78939818010469"),
        y = BigDecimal("55.1552073423335"),
        name = "Hezlett House",
        address = "107 Sea Road, Castlerock, Co. Londonderry",
        town = "Castlerock",
        postcode = "BT51 4TW",
        website = URI.create("http://www.discovernorthernireland.com/Hezlett-House-Castlerock-Coleraine-P8295"),
      )

      def assertResult(result: Seq[Either[CsvRecordDecoder.Error, UkCausewayCoast]]) = {
        assertTrue(result.count(_.isRight) == 19) &&
        assertTrue(result.apply(6) == Right(expected))
      }

      List(
        test("scala") {
          readFile(path) { input =>
            val result = CsvParser.decodeWithHeader(input, UkCausewayCoast.csvHeader, options)
            result match {
              case l @ Left(_) => assertTrue(l.isRight)
              case Right(result) => assertResult(result.toSeq)
            }
          }
        },
        testM("fs2") {
          val io = readFileFs2(path).through {
            Fs2CsvParser.decodeWithHeader(UkCausewayCoast.csvHeader, options)
          }.compile.toList
          catsIoToZio(io).map { result =>
            assertResult(result)
          }
        },
        testM("zio") {
          val stream = readFileZio(path)
          ZioCsvParser.decodeWithHeader(stream, UkCausewayCoast.csvHeader, options).use { s =>
            s.runCollect.mapError(Left(_))
          }.map { result =>
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
      val total = 1004118L
      assertTotal("uk-property-sales-price-paid-2019.csv", UkPropertySalesPricePaid.decoder, total)
    }*),
  )

  private def assertTotal[T](fileName: String, decoder: CsvRecordDecoder[T], total: Long) = {
    val path = Paths.get(getClass.getResource(s"/csv/$fileName").getPath)

    List(
      test("scala") {
        val result = readFile(path) { input =>
          CsvParser.decode(input, options)(decoder).count(_.isRight).toLong
        }
        assertTrue(result == total)
      },
      testM("fs2") {
        val io = readFileFs2(path).through {
          Fs2CsvParser.decode[IO, T](options)(implicitly, decoder)
        }.collect { case Right(v) => v }.compile.count
        catsIoToZio(io).map { count =>
          assertTrue(count == total)
        }
      },
      testM("zio") {
        val transducer = ZioCsvParser.decode(options)(decoder).mapError {
          case e: Error.LineTooLong => e
        }.mapM(ZIO.fromEither(_))
        readFileZio(path).transduce(transducer).runCount.map { count =>
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
          CsvParser.decodeWithHeader(input, header, options)
            .map(_.count(_.isRight).toLong)
        }
        assertTrue(result == Right(total))
      },
      testM("fs2") {
        val io = readFileFs2(path).through {
          Fs2CsvParser.decodeWithHeader(header, options)
        }.collect { case Right(v) => v }.compile.count
        catsIoToZio(io).map { count =>
          assertTrue(count == total)
        }
      },
      testM("zio") {
        val stream = readFileZio(path)
        ZioCsvParser.decodeWithHeader(stream, header, options).use { s =>
          s.collectRight.runCount.mapError(Left(_))
        }.map { count =>
          assertTrue(count == total)
        }
      },
    )
  }

  private def readFile[T](path: Path)(f: Iterator[String] => T) = {
    val is = Files.newInputStream(path)
    try {
      f(strings(is, StandardCharsets.UTF_8))
    } finally {
      is.close()
    }
  }

  private val UTF8BOM = List(0xef, 0xbb, 0xbf).map(_.toByte)

  private def strings(is: InputStream, cs: Charset) = new Iterator[String] {
    private var initial = true
    private var read = 0
    private val buffer = Array.ofDim[Byte](16384)

    override def hasNext = {
      if (read == 0) {
        read = is.read(buffer)
      }
      read >= 0
    }

    override def next() = {
      if (read < 0) {
        throw new NoSuchElementException
      } else {
        val _bytes = buffer.take(read)
        read = 0

        val bytes = if (initial) {
          initial = false
          if (_bytes.take(3).toList == UTF8BOM) _bytes.drop(3) else _bytes
        } else _bytes
        new String(bytes, cs)
      }
    }
  }

  private def readFileFs2(path: Path) = {
    fs2.io.file.Files[IO].readAll(fs2.io.file.Path.fromNioPath(path)).through(fs2.text.utf8.decode)
  }

  private def readFileZio(path: Path) = {
    ZStream.fromFile(path, chunkSize = 16384).transduce(ZTransducer.utfDecode)
  }

  override val aspects = List(
    TestAspect.timeout(60.seconds),
  )
}
