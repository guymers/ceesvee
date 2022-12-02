package ceesvee.zio

import _root_.zio.Cause
import _root_.zio.Scope
import _root_.zio.Trace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

object ZioCsvReader {
  import CsvParser.Error

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   */
  def decodeWithHeader[R, E, T](
    stream: ZStream[R, E, String],
    header: CsvHeader[T],
    options: CsvReader.Options,
  )(implicit
    trace: Trace,
  ): ZIO[Scope & R, Either[Either[E, Error], CsvHeader.MissingHeaders], ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]]] = {
    for {
      tuple <- ZioCsvParser.parseWithHeader(stream, options).mapError(Left(_))
      (headerFields, s) = tuple
      decoder <- header.create(headerFields) match {
        case Left(error) => ZIO.refailCause(Cause.fail(error)).mapError(Right(_))
        case Right(decoder) => ZIO.succeed(decoder)
      }
    } yield {
      s.map(decoder.decode(_))
    }
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvReader.Options,
  )(implicit
    D: CsvRecordDecoder[T],
    trace: Trace,
  ): ZPipeline[Any, Error, String, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParser.parse(options) >>> ZPipeline.map(D.decode(_))
  }
}
