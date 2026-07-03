package ceesvee.zio

import _root_.zio.Chunk
import _root_.zio.Ref
import _root_.zio.Trace as ZIOTrace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import ceesvee.CsvParser
import ceesvee.CsvParserVector

import java.nio.charset.StandardCharsets

object ZioCsvParserVector {
  import CsvParserVector.State
  import CsvParserVector.parseLine
  import CsvParserVector.splitBytes

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, Byte, Chunk[String]] = {
    _parse(State.initial, options)
  }

  private[ceesvee] def _parse(state: State, options: CsvParser.Options)(implicit trace: ZIOTrace) = {
    _splitLines(state, options) >>>
      ZPipeline.filter[Array[Byte]](bytes => !CsvParser.ignoreLine(new String(bytes, StandardCharsets.UTF_8), options)) >>>
      ZPipeline.map(parseLine[Chunk](_, options))
  }

  /**
   * Split strings into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   */
  def splitLines(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, Byte, String] = {
    _splitLines(State.initial, options).map(new String(_, StandardCharsets.UTF_8))
  }

  private def _splitLines(
    state: State,
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace) = ZPipeline.fromPush {
    Ref.make(state).map { stateRef => (chunk: Option[Chunk[Byte]]) =>
      chunk match {
        case None =>
          stateRef.getAndSet(State.initial).map { s =>
            if (s.leftover.isEmpty) Chunk.empty else Chunk(s.leftover)
          }

        case Some(bytes) =>
          stateRef.get.flatMap { s =>
            ZIO.fail(CsvParser.Error.LineTooLong(options.maximumLineLength))
              .when(s.leftover.length > options.maximumLineLength)
          } *> stateRef.modify(splitBytes[Chunk](bytes.toArray, _).swap)
      }
    }
  }
}
