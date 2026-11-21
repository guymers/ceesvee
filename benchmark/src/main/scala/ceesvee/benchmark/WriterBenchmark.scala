package ceesvee.benchmark

import ceesvee.CsvRecordEncoder
import ceesvee.CsvWriter
import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class WriterBenchmark {
  import WriterBenchmark.*

  @Benchmark
  def ceesveeFieldsToLine: List[String] = {
    fields.map(CsvWriter.fieldsToLine)
  }

  @Benchmark
  def ceesveeFieldsToLineWithoutQuoting: List[String] = {
    plainFields.map(CsvWriter.fieldsToLine)
  }

  @Benchmark
  def ceesveeEncodeWithHeader: List[String] = {
    CsvWriter.encodeWithHeader(header, rows.iterator).toList
  }
}
object WriterBenchmark {

  private val header = List("str", "optStr", "int", "float", "bool")

  private val fields: List[List[String]] = (1 to 1000).map { i =>
    List(
      "basic string",
      s""" "quoted \nstring" $i""",
      i.toString,
      "456.789",
      (i % 2 == 0).toString,
    )
  }.toList

  private val plainFields: List[List[String]] = (1 to 1000).map { i =>
    List(
      "basic string",
      "another string",
      i.toString,
      "456.789",
      "true",
    )
  }.toList

  private val rows: List[Row] = (1 to 1000).map { i =>
    Row("basic string", Some(s""" "quoted \nstring" $i"""), i, 456.789f, i % 2 == 0)
  }.toList

  case class Row(
    str: String,
    optStr: Option[String],
    int: Int,
    float: Float,
    bool: Boolean,
  ) derives CsvRecordEncoder

}
