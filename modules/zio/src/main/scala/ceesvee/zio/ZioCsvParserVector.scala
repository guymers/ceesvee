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
  import CsvParser.Error
  import CsvParserVector.State
  import CsvParserVector.parseLine
  import CsvParserVector.splitBytes

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, Error, Byte, Chunk[String]] = {
    val withoutIgnoredLines = if (CsvParser.canIgnoreLines(options)) {
      ZPipeline.filter[Array[Byte]](bytes => !CsvParser.ignoreLine(new String(bytes, StandardCharsets.UTF_8), options))
    } else {
      ZPipeline.identity[Array[Byte]]
    }

    _splitLines(options) >>>
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
  )(implicit trace: ZIOTrace): ZPipeline[Any, Error, Byte, String] = {
    _splitLines(options).map(new String(_, StandardCharsets.UTF_8))
  }

  private def _splitLines(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace) = ZPipeline.fromPush {
    Ref.make(State.initial).map { stateRef => (chunk: Option[Chunk[Byte]]) =>
      (chunk match {
        case None =>
          stateRef.getAndSet(State.initial).map { s =>
            if (s.leftover.isEmpty) Chunk.empty else Chunk(s.leftover)
          }

        case Some(bytes) =>
          stateRef.get.flatMap { s =>
            failWhenLineTooLong(s.leftover, options)
          } *> stateRef.modify(splitBytes[Chunk](bytes.toArray, _).swap)

      }).tap { bytes =>
        ZIO.foreachDiscard(bytes)(failWhenLineTooLong(_, options))
      }
    }
  }

  private def failWhenLineTooLong(bytes: Array[Byte], options: CsvParser.Options) =
    ZIO.fail(Error.LineTooLong(options.maximumLineLength))
      .when(bytes.length > options.maximumLineLength)
}
