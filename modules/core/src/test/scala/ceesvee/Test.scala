package ceesvee

case class Test(
  str: String,
  optStr: Option[String],
  int: Int,
  float: Float,
  bool: Boolean,
  optInt: Option[Int],
)
object Test {
  given decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derived
  given encoder: CsvRecordEncoder[Test] = CsvRecordEncoder.derived
}
