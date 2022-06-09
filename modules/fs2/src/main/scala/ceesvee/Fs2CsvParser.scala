package ceesvee

import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.RaiseThrowable
import fs2.Stream

import scala.collection.immutable.ArraySeq

object Fs2CsvParser {
  import CsvParser.Error
  import CsvParser.State
  import CsvParser.ignoreLine
  import CsvParser.parseLine
  import CsvParser.splitStrings

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   *
   * Raises a [[Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   *
   * Raises a [[CsvHeader.MissingHeaders]] if the first line does not contain
   * all the headers.
   */
  def decodeWithHeader[F[_]: RaiseThrowable, T](
    header: CsvHeader[T],
    options: CsvParser.Options,
  ): Pipe[F, String, Either[CsvRecordDecoder.Error, T]] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def go(
      stream: Stream[F, ArraySeq[String]],
      decoder: Option[CsvHeader.Decoder[T]],
    ): Pull[F, Either[CsvRecordDecoder.Error, T], Unit] =
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
   * Raises a [[Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   */
  def decode[F[_]: RaiseThrowable, T](
    options: CsvParser.Options,
  )(implicit D: CsvRecordDecoder[T]): Pipe[F, String, Either[CsvRecordDecoder.Error, T]] = {
    _.through(parse(options)).map(D.decode(_))
  }

  /**
   * Turns a stream of strings into a stream of CSV records.
   *
   * Raises a [[Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   */
  def parse[F[_]: RaiseThrowable](
    options: CsvParser.Options,
  ): Pipe[F, String, ArraySeq[String]] = {
    _.through(splitLines(options))
      .filter(str => !ignoreLine(str))
      .map(parseLine[ArraySeq](_))
  }

  /**
   * Split strings into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   *
   * Raises a [[Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   */
  def splitLines[F[_]: RaiseThrowable](
    options: CsvParser.Options,
  ): Pipe[F, String, String] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def go(stream: Stream[F, String], state: State, first: Boolean): Pull[F, String, Unit] =
      stream.pull.uncons.flatMap {
        case None if first => Pull.done // fs2.text.lines does this so I assume it is important

        case None =>
          if (state.leftover.isEmpty) Pull.output(Chunk.empty)
          else Pull.output1(state.leftover)

        case Some((chunk, stream)) =>
          val (newState, lines) = splitStrings(chunk.toArraySeq, state)

          if (newState.leftover.length > options.maximumLineLength) {
            Pull.raiseError[F](Error.LineTooLong(options.maximumLineLength))
          } else {
            Pull.output(Chunk.indexedSeq(lines)) >> go(stream, newState, first = false)
          }
      }

    s => Stream.suspend(go(s, State.initial, first = true).stream)
  }
}
