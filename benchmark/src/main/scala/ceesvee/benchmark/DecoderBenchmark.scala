package ceesvee.benchmark

import ceesvee.CsvRecordDecoder
import ceesvee.benchmark.data.TestDecodeJavaIndex
import ceesvee.benchmark.data.TestDecodeScala
import com.univocity.parsers.common.processor.BeanListProcessor
import com.univocity.parsers.common.record.Record
import com.univocity.parsers.common.record.RecordMetaData
import org.openjdk.jmh.annotations.*

import java.util.concurrent.TimeUnit
import scala.collection.immutable.ArraySeq

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class DecoderBenchmark {

  private val row = Array(
    /*str*/ "basic string",
    /*optStr*/ "",
    /*int*/ "123",
    /*float*/ "456.789",
    /*bool*/ "true",
    /*optInt*/ "5",
  )
  private val rowIndexedSeq = ArraySeq.unsafeWrapArray(row)

  @Benchmark
  def ceesvee: Either[CsvRecordDecoder.Errors, TestDecodeScala] = {
    TestDecodeScala.decoder.decode(rowIndexedSeq)
  }

  private val univocityProcessor = {
    new BeanListProcessor(classOf[TestDecodeJavaIndex])
  }

  private val univocityEmptyContext: com.univocity.parsers.common.Context = {
    new com.univocity.parsers.common.Context {
      override val headers = Array.empty[String]
      override val selectedHeaders = null
      override val extractedFieldIndexes = null
      override val columnsReordered = true
      override def indexOf(header: String) = -1
      override def indexOf(header: Enum[?]) = -1
      override val currentColumn = 0
      override val currentRecord = 0L
      override def stop(): Unit = ()
      override val isStopped = false
      override val errorContentLength = 0
      override def toRecord(row: Array[String]): Record = null
      override def recordMetaData(): RecordMetaData = null
    }
  }

  @Benchmark
  def univocity: TestDecodeJavaIndex = {
    univocityProcessor.createBean(row, univocityEmptyContext)
  }
}
