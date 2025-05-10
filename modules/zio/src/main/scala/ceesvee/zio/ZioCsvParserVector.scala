package ceesvee.zio

import _root_.zio.Chunk
import _root_.zio.Ref
import _root_.zio.Scope
import _root_.zio.Trace as ZIOTrace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZStream
import ceesvee.CsvParser
import ceesvee.CsvParserVector
import ceesvee.CsvReader

import java.nio.charset.Charset

object ZioCsvParserVector {
  import CsvParserVector.State
  import CsvParserVector.parseLine
  import CsvParserVector.splitBytes

  /**
   * Turns a stream of strings into a stream of CSV records extracting the first
   * record.
   */
  def parseWithHeader[R, E](
    stream: ZStream[R, E, Byte],
    charset: Charset,
    options: CsvReader.Options,
  )(implicit
    trace: ZIOTrace,
  ): ZIO[Scope & R, Error[E], (Chunk[String], ZStream[Any, Error[E], Chunk[String]])] = {
    stream.mapError(Left(_)).peel {
      extractFirstLine(charset, options).mapError(Right(_))
    }.map { case ((headers, state, records), s) =>
      (headers, ZStream.fromChunk(records) ++ (s >>> _parse(state, charset, options).mapError(Right(_))))
    }
  }

  private def extractFirstLine(charset: Charset, options: CsvReader.Options)(implicit trace: ZIOTrace) = {
    def process(state: State, bytes: Chunk[Byte]) = {
      val (newState, lines) = splitBytes(bytes.toArray, state)
      val records = lines.map(parseLine[Chunk](_, charset, options)).filter(_ != null)
      (newState, records)
    }

    ZioCsvParser.extractFirstLine_(State.initial, options)(_.leftover.length, process)
  }

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    charset: Charset,
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, Byte, Chunk[String]] = {
    _parse(State.initial, charset, options)
  }

  private[ceesvee] def _parse(state: State, charset: Charset, options: CsvParser.Options)(implicit trace: ZIOTrace) = {
    _splitLines(state, options) >>>
      ZPipeline.map(parseLine[Chunk](_, charset, options)) >>>
      ZPipeline.filter[Chunk[String]](_ != null)
  }

  /**
   * Split strings into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   */
  def splitLines(
    charset: Charset,
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, Byte, String] = {
    _splitLines(State.initial, options).map(new String(_, charset))
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
