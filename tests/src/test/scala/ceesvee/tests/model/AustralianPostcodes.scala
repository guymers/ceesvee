package ceesvee.tests.model

import ceesvee.CsvHeader
import ceesvee.CsvRecordDecoder
import zio.NonEmptyChunk

case class AustralianPostcodes(
  suburb: String,
  state: String,
  postcode: Int,
)
object AustralianPostcodes {

  val header = NonEmptyChunk("Suburb", "State", "Zip")

  implicit val decoder: CsvRecordDecoder[AustralianPostcodes] = CsvRecordDecoder.derived
  val csvHeader = CsvHeader.create(header.toCons)(decoder)
}
