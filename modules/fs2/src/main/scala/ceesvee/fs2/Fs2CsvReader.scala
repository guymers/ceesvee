package ceesvee.fs2

import _root_.fs2.Chunk
import _root_.fs2.Pipe
import _root_.fs2.Pull
import _root_.fs2.RaiseThrowable
import _root_.fs2.Stream
import ceesvee.CsvHeader
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

import scala.collection.immutable.ArraySeq

object Fs2CsvReader {
  import Fs2CsvParser.parse

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   *
   * Raises a [[ceesvee.CsvParser.Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   *
   * Raises a [[CsvHeader.MissingHeaders]] if the first line does not contain
   * all the headers.
   */
  def decodeWithHeader[F[_]: RaiseThrowable, T](
    header: CsvHeader[T],
    options: CsvReader.Options,
  ): Pipe[F, String, Either[CsvHeader.Errors, T]] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def go(
      stream: Stream[F, ArraySeq[String]],
      decoder: Option[CsvHeader.Decoder[T]],
    ): Pull[F, Either[CsvHeader.Errors, T], Unit] =
      stream.pull.uncons.flatMap {

        case None =>
          Pull.output(Chunk.empty)

        case Some((chunk, stream)) =>
          decoder.map(d => Right((chunk, d))).orElse {
            chunk.head.map { headerFields =>
              header.create(headerFields).map { decoder =>
                (chunk.drop(1), decoder)
              }
            }
          } match {
            case None => go(stream, decoder)
            case Some(Left(e)) => Pull.raiseError[F](e)
            case Some(Right((records, decoder))) =>
              Pull.output(records.map(decoder.decode(_))) >> go(stream, Some(decoder))
          }
      }

    s => {
      val _s = s.through(parse(options))

      Stream.suspend(go(_s, None).stream)
    }
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * Raises a [[ceesvee.CsvParser.Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   */
  def decode[F[_]: RaiseThrowable, T](
    options: CsvReader.Options,
  )(implicit D: CsvRecordDecoder[T]): Pipe[F, String, Either[CsvRecordDecoder.Errors, T]] = {
    _.through(parse(options)).map(D.decode(_))
  }
}
