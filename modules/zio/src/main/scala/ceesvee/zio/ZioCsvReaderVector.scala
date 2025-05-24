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
  import CsvParser.Error

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
  ): ZIO[Scope & R, Either[Either[E, Error], CsvHeader.MissingHeaders], ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]]] = {
    for {
      tuple <- ZioCsvParserVector.parseWithHeader(stream, charset, options).mapError(Left(_))
      (headerFields, s) = tuple
      decoder <- header.create(headerFields) match {
        case Left(error) => Exit.fail(error).mapError(Right(_))
        case Right(decoder) => Exit.succeed(decoder)
      }
    } yield {
      s.map(decoder.decode(_))
    }
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
  ): ZPipeline[Any, Error, Byte, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParserVector.parse(charset, options) >>> ZPipeline.map(D.decode(_))
  }
}
