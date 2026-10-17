package ceesvee.zio

import _root_.zio.Cause
import _root_.zio.Chunk
import _root_.zio.Trace as ZIOTrace
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

object ZioCsvReader {

  type Error = CsvParser.Error.LineTooLong | CsvHeader.MissingHeaders
  object Error {
    type LineTooLong = CsvParser.Error.LineTooLong
    type MissingHeaders = CsvHeader.MissingHeaders

    val LineTooLong: CsvParser.Error.LineTooLong.type = CsvParser.Error.LineTooLong
    val MissingHeaders: CsvHeader.MissingHeaders.type = CsvHeader.MissingHeaders
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   */
  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.TryPartial", "org.wartremover.warts.Var"))
  def decodeWithHeader[R, E, T](
    stream: ZStream[R, E, String],
    header: CsvHeader[T],
    options: CsvReader.Options,
  )(using
    trace: ZIOTrace,
  ): ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]] = {
    decodeWithHeader_(stream, header)(ZioCsvParser.parse(options))
  }

  @SuppressWarnings(Array("org.wartremover.warts.Null", "org.wartremover.warts.TryPartial", "org.wartremover.warts.Var"))
  private[zio] def decodeWithHeader_[R, E, A, T](
    stream: ZStream[R, E, A],
    header: CsvHeader[T],
  )(
    parse: ZPipeline[Any, CsvParser.Error, A, Chunk[String]],
  )(using
    trace: ZIOTrace,
  ): ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]] = ZStream.suspend {
    var decoder: CsvHeader.Decoder[T] = null

    stream.mapError(Left(_)).via {
      parse.mapError {
        case e: CsvParser.Error.LineTooLong => Right(e)
      }
    }.map { fields =>
      if (decoder eq null) {
        decoder = header.create(fields).toTry.get
        null
      } else {
        decoder.decode(fields)
      }
    }.filter(_ ne null).catchSomeCause {
      case Cause.Die(e: CsvParser.Error.LineTooLong, _) => ZStream.fail(Right(e))
      case Cause.Die(e: CsvHeader.MissingHeaders, _) => ZStream.fail(Right(e))
    } ++ (if (decoder eq null) ZStream.fail(Right(CsvHeader.MissingHeaders(header.headers))) else ZStream.empty)
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvReader.Options,
  )(using
    D: CsvRecordDecoder[T],
    trace: ZIOTrace,
  ): ZPipeline[Any, CsvParser.Error, String, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParser.parse(options) >>> ZPipeline.map(D.decode(_))
  }
}
