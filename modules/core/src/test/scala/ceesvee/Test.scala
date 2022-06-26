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
  implicit val decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derive
  implicit val encoder: CsvRecordEncoder[Test] = CsvRecordEncoder.derive
}
