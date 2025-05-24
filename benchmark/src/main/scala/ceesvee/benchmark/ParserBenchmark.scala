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
@Fork(
  jvmArgs = Array(
    "--enable-preview",
    "--add-modules=jdk.incubator.vector",
  ),
)
class ParserBenchmark {

  private def line(i: Int) = List("basic string", " \"quoted \nstring\" ", i.toString, "456.789", "true").mkString(",")

  private val lines = (1 to 1000).map(line(_)).mkString("\n")
  private val linesBytes = lines.getBytes(StandardCharsets.UTF_8)
  private def linesReader = {
    val streams = new java.util.ArrayList[ByteArrayInputStream]()
    linesBytes.grouped(8192).foreach { bytes =>
      streams.add(new ByteArrayInputStream(bytes))
    }
    val is = new SequenceInputStream(java.util.Collections.enumeration(streams))
    new InputStreamReader(is)
  }

  private val ceesveeOptions = {
    _root_.ceesvee.CsvParser.Options.Defaults
  }

  @Benchmark
  def ceesvee: List[List[String]] = {
    _root_.ceesvee.CsvParser.parse[List](lines.grouped(8192), ceesveeOptions).toList
  }

  @Benchmark
  def ceesveeVector: List[List[String]] = {
    _root_.ceesvee.CsvParser.parseVector[List](linesBytes.grouped(8192), ceesveeOptions, StandardCharsets.UTF_8).toList
  }

  @Benchmark
  def scalaCsv: List[List[String]] = {
    import com.github.tototoshi.csv.CSVFormat.defaultCSVFormat
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
