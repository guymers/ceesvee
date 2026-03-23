package ceesvee.tests.model

import ceesvee.CsvHeader
import ceesvee.CsvRecordDecoder
import zio.NonEmptyChunk

case class CapeIvyPopulationGenomics(
  id: String,
  pop: String,
  state: String,
  country: String,
  stipulate: String,
)
object CapeIvyPopulationGenomics {

  val header = NonEmptyChunk("id", "pop", "state", "country", "stipulate")

  implicit val decoder: CsvRecordDecoder[CapeIvyPopulationGenomics] = CsvRecordDecoder.derived
  val csvHeader = CsvHeader.create(header.toCons)(decoder)
}
