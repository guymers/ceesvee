package ceesvee.zio

import _root_.zio.Chunk
import _root_.zio.Ref
import _root_.zio.ZIO
import _root_.zio.stream.ZTransducer
import ceesvee.CsvParser
import ceesvee.CsvReader

object ZioCsvParser {
  import CsvParser.Error
  import CsvParser.State
  import CsvParser.ignoreLine
  import CsvParser.parseLine
  import CsvParser.splitStrings

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    options: CsvReader.Options,
  ): ZTransducer[Any, Error, String, Chunk[String]] = {
    _parse(State.initial, options)
  }

  private[ceesvee] def _parse(state: State, options: CsvReader.Options) = {
    _splitLines(state, options)
      .filterInput[String](str => !ignoreLine(str))
      .map(parseLine[Chunk](_))
  }

  /**
   * Split strings into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   */
  def splitLines(
    options: CsvReader.Options,
  ): ZTransducer[Any, Error, String, String] = {
    _splitLines(State.initial, options)
  }

  private def _splitLines(
    state: State,
    options: CsvReader.Options,
  ): ZTransducer[Any, Error, String, String] = ZTransducer {
    Ref.makeManaged(state).map { stateRef =>
      {
        case None =>
          stateRef.getAndSet(State.initial).map { case State(leftover, _) =>
            if (leftover.isEmpty) Chunk.empty else Chunk(leftover)
          }

        case Some(strings) =>
          stateRef.get.flatMap { case State(leftover, _) =>
            ZIO.fail(Error.LineTooLong(options.maximumLineLength)).when(leftover.length > options.maximumLineLength)
          } *> stateRef.modify(splitStrings(strings, _).swap)
      }
    }
  }
}
