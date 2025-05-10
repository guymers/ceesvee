package ceesvee.zio

import _root_.zio.Chunk
import _root_.zio.Exit
import _root_.zio.Scope
import _root_.zio.Trace as ZIOTrace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

object ZioCsvReader {

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
    trace: ZIOTrace,
  ): ZIO[Scope & R, Either[Error[E], CsvHeader.MissingHeaders], ZStream[R, Error[E], Either[CsvHeader.Errors, T]]] = {
    decodeWithHeader_[R, E, T](ZioCsvParser.parseWithHeader(stream, options), header)
  }

  private[zio] def decodeWithHeader_[R, E, T](
    parseWithHeader: ZIO[Scope & R, Error[E], (Chunk[String], ZStream[Any, Error[E], Chunk[String]])],
    header: CsvHeader[T],
  )(implicit
    trace: ZIOTrace,
  ): ZIO[Scope & R, Either[Error[E], CsvHeader.MissingHeaders], ZStream[R, Error[E], Either[CsvHeader.Errors, T]]] = for {
    tuple <- parseWithHeader.mapError(Left(_))
    (headerFields, s) = tuple
    decoder <- header.create(headerFields) match {
      case Left(error) => Exit.fail(error).mapError(Right(_))
      case Right(decoder) => Exit.succeed(decoder)
    }
  } yield {
    s.map(decoder.decode(_))
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvReader.Options,
  )(implicit
    D: CsvRecordDecoder[T],
    trace: ZIOTrace,
  ): ZPipeline[Any, CsvParser.Error, String, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParser.parse(options) >>> ZPipeline.map(D.decode(_))
  }
}
