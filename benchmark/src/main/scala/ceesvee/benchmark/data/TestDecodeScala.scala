package ceesvee.benchmark.data

case class TestDecodeScala(
  str: String,
  optStr: Option[String],
  int: Int,
  float: Float,
  bool: Boolean,
  optInt: Option[Int],
)
object TestDecodeScala {
  val decoder: ceesvee.CsvRecordDecoder[TestDecodeScala] = ceesvee.CsvRecordDecoder.derived
}
