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
   * Turns a stream of bytes into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   *
   * The bytes are interpreted as UTF-8. A UTF-8 BOM is not stripped: drop the
   * leading bytes before calling this method.
   *
   * Fails with [[Error.MissingHeaders]] if the first line does not contain all
   * the headers, including when the input is empty.
   *
   * Requires the Java vector API to be available at runtime:
   * `--add-modules=jdk.incubator.vector`.
   */
  def decodeWithHeader[R, E, T](
    stream: ZStream[R, E, Byte],
    header: CsvHeader[T],
    options: CsvReader.Options,
  )(using ZIOTrace): ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]] = {
    val parse = ZioCsvParserVector.parse(options)
    ZioCsvReader.decodeWithHeader_(stream, header)(parse)
  }

  /**
   * Turns a stream of bytes into a stream of decoded CSV records.
   *
   * The bytes are interpreted as UTF-8.
   *
   * Requires the Java vector API to be available at runtime:
   * `--add-modules=jdk.incubator.vector`.
   */
  def decode[T](
    options: CsvReader.Options,
  )(using D: CsvRecordDecoder[T], trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, Byte, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParserVector.parse(options) >>> ZPipeline.map(D.decode(_))
  }
}
