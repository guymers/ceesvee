package ceesvee.tests.model

import ceesvee.CsvHeader
import ceesvee.CsvRecordDecoder
import zio.NonEmptyChunk

import java.net.URI

case class UkCausewayCoast(
  x: BigDecimal,
  y: BigDecimal,
  name: String,
  address: String,
  town: String,
  postcode: String,
  website: URI,
)
object UkCausewayCoast {

  // note ignoring "OBJECTID" header, "Town" swapped with "Postcode"
  val header = NonEmptyChunk("X", "Y", "Name", "Address", "Town", "Postcode", "Website")

  implicit val decoder: CsvRecordDecoder[UkCausewayCoast] = CsvRecordDecoder.derive
  val csvHeader = CsvHeader.create(header.toCons)(decoder)
}
