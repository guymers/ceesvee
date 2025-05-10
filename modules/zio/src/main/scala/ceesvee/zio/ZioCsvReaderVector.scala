package ceesvee.zio

import _root_.zio.Scope
import _root_.zio.Trace as ZIOTrace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder
import zio.Exit

import java.nio.charset.Charset

object ZioCsvReaderVector {

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   */
  def decodeWithHeader[R, E, T](
    stream: ZStream[R, E, Byte],
    header: CsvHeader[T],
    charset: Charset,
    options: CsvReader.Options,
  )(implicit
    trace: ZIOTrace,
  ): ZIO[Scope & R, Either[Error[E], CsvHeader.MissingHeaders], ZStream[R, Error[E], Either[CsvHeader.Errors, T]]] = {
    val parser = ZioCsvParserVector.parseWithHeader(stream, charset, options)
    ZioCsvReader.decodeWithHeader_[R, E, T](parser, header)
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    charset: Charset,
    options: CsvReader.Options,
  )(implicit
    D: CsvRecordDecoder[T],
    trace: ZIOTrace,
  ): ZPipeline[Any, CsvParser.Error, Byte, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParserVector.parse(charset, options) >>> ZPipeline.map(D.decode(_))
  }
}
