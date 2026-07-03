package ceesvee.zio

import _root_.zio.Trace as ZIOTrace
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

object ZioCsvReaderVector {
  import ZioCsvReader.Error

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   */
  def decodeWithHeader[R, E, T](
    stream: ZStream[R, E, Byte],
    header: CsvHeader[T],
    options: CsvReader.Options,
  )(implicit
    trace: ZIOTrace,
  ): ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]] = {
    val parse = ZioCsvParserVector.parse(options)
    ZioCsvReader.decodeWithHeader_(stream, header)(parse)
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvReader.Options,
  )(implicit
    D: CsvRecordDecoder[T],
    trace: ZIOTrace,
  ): ZPipeline[Any, CsvParser.Error, Byte, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParserVector.parse(options) >>> ZPipeline.map(D.decode(_))
  }
}
