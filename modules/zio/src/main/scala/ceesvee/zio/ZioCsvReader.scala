package ceesvee.zio

import _root_.zio.Cause
import _root_.zio.Trace
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

import scala.util.control.NoStackTrace

object ZioCsvReader {

  // replace with a union instead of redefining when removing Scala 2 support
  sealed trait Error
  object Error {
    final case class LineTooLong(maximum: Int)
      extends RuntimeException(s"CSV line exceeded maximum length of ${maximum.toString}")
      with Error

    final case class MissingHeaders(missing: ::[String])
      extends RuntimeException(s"Missing headers: ${missing.mkString(", ")}")
      with NoStackTrace
      with Error
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
  )(implicit
    trace: Trace,
  ): ZStream[R, Either[E, Error], Either[CsvHeader.Errors, T]] = ZStream.suspend {
    var decoder: CsvHeader.Decoder[T] = null

    stream.mapError(Left(_)).via {
      ZioCsvParser.parse(options).mapError {
        case CsvParser.Error.LineTooLong(maximum) => Right(Error.LineTooLong(maximum))
      }
    }.map { fields =>
      if (decoder eq null) {
        decoder = header.create(fields).left.map {
          case CsvHeader.MissingHeaders(missing) => Error.MissingHeaders(missing)
        }.toTry.get
        null
      } else {
        decoder.decode(fields)
      }
    }.filter(_ ne null).catchSomeCause {
      case Cause.Die(e: Error, _) => ZStream.fail(Right(e))
    } ++ (if (decoder eq null) ZStream.fail(Right(Error.MissingHeaders(header.headers))) else ZStream.empty)
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvReader.Options,
  )(implicit
    D: CsvRecordDecoder[T],
    trace: Trace,
  ): ZPipeline[Any, CsvParser.Error, String, Either[CsvRecordDecoder.Errors, T]] = {
    ZioCsvParser.parse(options) >>> ZPipeline.map(D.decode(_))
  }
}
