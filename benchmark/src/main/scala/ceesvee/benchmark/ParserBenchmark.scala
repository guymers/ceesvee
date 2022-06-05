package ceesvee.benchmark

import org.openjdk.jmh.annotations.*

import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class ParserBenchmark {

  private def line(i: Int) = List("basic string", " \"quoted \nstring\" ", i.toString, "456.789", "true").mkString(",")

  private val lines = (1 to 1000).map(line(_)).mkString("\n")
  private def linesChunked = lines.grouped(8192)
  private def linesReader = {
    val streams = new java.util.ArrayList[ByteArrayInputStream]()
    linesChunked.foreach { str =>
      streams.add(new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)))
    }
    val is = new SequenceInputStream(java.util.Collections.enumeration(streams));
    new InputStreamReader(is)
  }

  private val ceesveeOptions = {
    _root_.ceesvee.CsvParser.Options(
      maximumLineLength = 10000,
    )
  }

  @Benchmark
  def ceesvee: List[List[String]] = {
    _root_.ceesvee.CsvParser.parse[List](linesChunked, ceesveeOptions).toList
  }

  @Benchmark
  def scalaCsv: List[List[String]] = {
    import com.github.tototoshi.csv.defaultCSVFormat
    com.github.tototoshi.csv.CSVReader.open(linesReader).all()
  }

  private val univocitySettings = {
    val settings = new com.univocity.parsers.csv.CsvParserSettings
    settings.setReadInputOnSeparateThread(false)
    settings
  }

  @Benchmark
  def univocity: java.util.List[Array[String]] = {
    val parser = new com.univocity.parsers.csv.CsvParser(univocitySettings)
    parser.beginParsing(linesReader)
    parser.parseAll()
  }
}
