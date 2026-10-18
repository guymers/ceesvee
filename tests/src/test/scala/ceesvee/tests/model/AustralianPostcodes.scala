package ceesvee.tests.model

import ceesvee.CsvHeader
import ceesvee.CsvRecordDecoder
import zio.NonEmptyChunk

case class AustralianPostcodes(
  suburb: String,
  state: String,
  postcode: Int,
) derives CsvRecordDecoder
object AustralianPostcodes {

  val header = NonEmptyChunk("Suburb", "State", "Zip")

  val csvHeader = CsvHeader.create(header.toCons)
}
