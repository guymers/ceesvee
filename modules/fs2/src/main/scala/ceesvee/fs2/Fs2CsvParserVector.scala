package ceesvee.fs2

import _root_.fs2.Chunk
import _root_.fs2.Pipe as fs2Pipe
import _root_.fs2.Pull
import _root_.fs2.RaiseThrowable
import _root_.fs2.Stream
import ceesvee.CsvParser
import ceesvee.CsvParserVector

import java.nio.charset.StandardCharsets
import scala.collection.immutable.ArraySeq

object Fs2CsvParserVector {
  import CsvParser.Error
  import CsvParserVector.State
  import CsvParserVector.parseLine
  import CsvParserVector.splitBytes

  /**
   * Turns a stream of bytes into a stream of CSV records.
   *
   * The bytes are interpreted as UTF-8 and may be split across chunks at any
   * point.
   *
   * Requires the Java vector API to be available at runtime:
   * `--add-modules=jdk.incubator.vector`.
   *
   * Raises a [[CsvParser.Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   */
  def parse[F[_]: RaiseThrowable](
    options: CsvParser.Options,
  ): fs2Pipe[F, Byte, ArraySeq[String]] = {
    val withoutIgnoredLines: fs2Pipe[F, Array[Byte], Array[Byte]] =
      if (CsvParser.canIgnoreLines(options)) {
        _.filter(bytes => !CsvParser.ignoreLine(new String(bytes, Utf8), options))
      } else {
        identity
      }

    _.through(splitLines(options))
      .through(withoutIgnoredLines)
      .map(parseLine[ArraySeq](_, options))
  }

  /**
   * Split bytes into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   *
   * The bytes are interpreted as UTF-8 and may be split across chunks at any
   * point.
   *
   * Requires the Java vector API to be available at runtime:
   * `--add-modules=jdk.incubator.vector`.
   *
   * Raises a [[CsvParser.Error.LineTooLong]] if a line is longer than
   * `maximumLineLength`.
   */
  def splitLines[F[_]: RaiseThrowable](
    options: CsvParser.Options,
  ): fs2Pipe[F, Byte, Array[Byte]] = {

    @SuppressWarnings(Array("org.wartremover.warts.Recursion"))
    def go(stream: Stream[F, Byte], state: State, first: Boolean): Pull[F, Array[Byte], Unit] =
      stream.pull.uncons.flatMap {
        case None if first => Pull.done

        case None =>
          if (state.leftover.isEmpty) Pull.output(Chunk.empty)
          else Pull.output1(state.leftover)

        case Some((chunk, stream)) =>
          val (newState, lines) = splitBytes[List](chunk.toArray, state)

          if (newState.leftover.length > options.maximumLineLength || lines.exists(_.length > options.maximumLineLength)) {
            Pull.raiseError[F](Error.LineTooLong(options.maximumLineLength))
          } else {
            Pull.output(Chunk.from(lines)) >> go(stream, newState, first = false)
          }
      }

    s => Stream.suspend(go(s, State.initial, first = true).stream)
  }

  private val Utf8 = StandardCharsets.UTF_8
}
