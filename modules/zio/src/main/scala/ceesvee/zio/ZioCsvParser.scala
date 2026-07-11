package ceesvee.zio

import _root_.zio.Chunk
import _root_.zio.Ref
import _root_.zio.Trace as ZIOTrace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import ceesvee.CsvParser

object ZioCsvParser {
  import CsvParser.Error
  import CsvParser.State
  import CsvParser.canIgnoreLines
  import CsvParser.ignoreLine
  import CsvParser.parseLine
  import CsvParser.splitStrings

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, Error, String, Chunk[String]] = {
    val withoutIgnoredLines = if (canIgnoreLines(options)) {
      ZPipeline.filter[String](str => !ignoreLine(str, options))
    } else {
      ZPipeline.identity[String]
    }

    splitLines(options) >>>
      withoutIgnoredLines >>>
      ZPipeline.map(parseLine[Chunk](_, options))
  }

  /**
   * Split strings into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   */
  def splitLines(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, String, String] = ZPipeline.fromPush {
    Ref.make(State.initial).map { stateRef => (chunk: Option[Chunk[String]]) =>
      (chunk match {
        case None =>
          stateRef.getAndSet(State.initial).map { case State(leftover, _, _) =>
            if (leftover.isEmpty) Chunk.empty else Chunk(leftover)
          }

        case Some(strings) =>
          stateRef.get.flatMap { case State(leftover, _, _) =>
            failWhenLineTooLong(leftover, options)
          } *> stateRef.modify(splitStrings(strings, _).swap)

      }).tap { lines =>
        ZIO.foreachDiscard(lines)(failWhenLineTooLong(_, options))
      }
    }
  }

  private def failWhenLineTooLong(line: String, options: CsvParser.Options) =
    ZIO.fail(Error.LineTooLong(options.maximumLineLength))
      .when(line.length > options.maximumLineLength)
}
