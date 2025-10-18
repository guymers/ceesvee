package ceesvee.fs2

import _root_.fs2.Chunk
import _root_.fs2.Pipe
import _root_.fs2.Pull
import _root_.fs2.RaiseThrowable
import _root_.fs2.Stream
import ceesvee.CsvParser

import scala.collection.immutable.ArraySeq

object Fs2CsvParser {
  import CsvParser.Error
  import CsvParser.State
  import CsvParser.ignoreLine
  import CsvParser.parseLine
  import CsvParser.splitStrings

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
      .filter(str => !ignoreLine(str, options))
      .map(parseLine[ArraySeq](_, options))
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
            Pull.output(Chunk.from(lines)) >> go(stream, newState, first = false)
          }
      }

    s => Stream.suspend(go(s, State.initial, first = true).stream)
  }
}
