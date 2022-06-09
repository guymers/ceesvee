package ceesvee.tests.model

import ceesvee.CsvHeader
import ceesvee.CsvRecordDecoder
import zio.NonEmptyChunk

case class NZGreenhouseGasEmissions(
  year: Int,
  anzwi: Option[String],
  anzsic: String,
  anzsicDescriptor: String,
  category: String,
  variable: String,
  units: String,
  magnitude: String,
  source: String,
  value: BigDecimal,
)
object NZGreenhouseGasEmissions {

  // note "anzwi" swapped with "anzsic"
  val header = NonEmptyChunk(
    "year",
    "anzwi",
    "anzsic",
    "anzsic_descriptor",
    "category",
    "variable",
    "units",
    "magnitude",
    "source",
    "data_value",
  )

  implicit val decoder: CsvRecordDecoder[NZGreenhouseGasEmissions] = CsvRecordDecoder.derive
  val csvHeader = CsvHeader.create(header.toCons)(decoder)
}
