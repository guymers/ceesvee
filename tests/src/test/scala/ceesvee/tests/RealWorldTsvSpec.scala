package ceesvee.tests

import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.fs2.Fs2CsvReader
import ceesvee.tests.model.CapeIvyPopulationGenomics
import ceesvee.zio.ZioCsvReader
import zio.durationInt
import zio.test.TestAspect
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import java.nio.file.Paths

object RealWorldTsvSpec extends ZIOSpecDefault {
  import RealWorldFileHelper.*

  private val options = CsvReader.Options.Defaults.copy(
    delimiter = CsvParser.Options.Delimiter.Tab,
  )

  override val spec = suite("RealWorldTsv")(
    suite("Population genomics of Cape ivy")({
      val path = Paths.get(getClass.getResource("/tsv/cape-ivy-population-genomics.tsv").getPath)

      val expected = CapeIvyPopulationGenomics(
        id = "1.18",
        pop = "Penola",
        state = "South_Australia",
        country = "Australia",
        stipulate = "Unknown",
      )

      def assertResult(result: Seq[Either[CsvHeader.Errors, CapeIvyPopulationGenomics]]) = {
        assertTrue(result.count(_.isRight) == 1089) &&
        assertTrue(result.apply(8) == Right(expected))
      }

      List(
        test("scala") {
          readFile(path) { input =>
            val result = CsvReader.decodeWithHeader(input, CapeIvyPopulationGenomics.csvHeader, options)
            result match {
              case l @ Left(_) => assertTrue(l.isRight)
              case Right(result) => assertResult(result.toSeq)
            }
          }
        },
        test("fs2") {
          val io = readFileFs2(path).through {
            Fs2CsvReader.decodeWithHeader(CapeIvyPopulationGenomics.csvHeader, options)
          }.compile.toList
          catsIoToZio(io).map { result =>
            assertResult(result)
          }
        },
        test("zio") {
          val stream = readFileZio(path)
          ZioCsvReader.decodeWithHeader(stream, CapeIvyPopulationGenomics.csvHeader, options)
            .runCollect
            .map { result =>
              assertResult(result)
            }
        },
      )
    }*),
  ) @@ TestAspect.timeout(60.seconds) @@ TestAspect.timed
}
