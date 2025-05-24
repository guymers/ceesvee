package ceesvee.zio

import _root_.zio.Cause
import _root_.zio.Chunk
import _root_.zio.NonEmptyChunk
import _root_.zio.Ref
import _root_.zio.Scope
import _root_.zio.Trace as ZIOTrace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZSink
import _root_.zio.stream.ZStream
import ceesvee.CsvParser
import ceesvee.CsvParserVector
import ceesvee.CsvReader

import java.nio.charset.Charset

object ZioCsvParserVector {
  import CsvParser.Error
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
  ): ZIO[Scope & R, Either[E, Error], (Chunk[String], ZStream[Any, Either[E, Error], Chunk[String]])] = {
    stream.mapError(Left(_)).peel {
      extractFirstLine(charset, options).mapError(Right(_))
    }.map { case ((headers, state, records), s) =>
      (headers, ZStream.fromChunk(records) ++ (s >>> _parse(state, charset, options).mapError(Right(_))))
    }
  }

  private def extractFirstLine(charset: Charset, options: CsvReader.Options)(implicit trace: ZIOTrace) = {

    val initial: Chunk[Chunk[String]] = Chunk.empty

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def done(state: State, records: Chunk[Chunk[String]]) = {
      NonEmptyChunk.fromChunk(records).map { rs =>
        Push.emit((rs.head, state, rs.tail), Chunk.empty)
      }
    }

    val push = Ref.make((State.initial, initial)).map { stateRef => (chunk: Option[Chunk[Byte]]) =>
      chunk match {
        case None => stateRef.get.flatMap { case (state, lines) =>
            done(state, lines).getOrElse(Push.emit((Chunk.empty, state, lines), Chunk.empty))
          }

        case Some(bytes) =>
          stateRef.get.flatMap { case (state, records) =>
            if (state.leftover.length > options.maximumLineLength) {
              Push.fail(Error.LineTooLong(options.maximumLineLength), Chunk.empty)
            } else {
              val (newState, lines) = splitBytes(bytes.toArray, state)
              val moreRecords = lines.map(parseLine[Chunk](_, charset, options)).filter(_ != null)
              val _records = records ++ moreRecords
              done(newState, _records).getOrElse(stateRef.set((newState, _records)) *> Push.more)
            }
          }
      }
    }

    ZSink.fromPush(push)
  }

  private object Push {
    val more: ZIO[Any, Nothing, Unit] = ZIO.unit
    def emit[I, Z](z: Z, leftover: Chunk[I]): ZIO[Any, (Right[Nothing, Z], Chunk[I]), Nothing] =
      ZIO.refailCause(Cause.fail((Right(z), leftover)))
    def fail[I, E](e: E, leftover: Chunk[I]): ZIO[Any, (Left[E, Nothing], Chunk[I]), Nothing] =
      ZIO.fail((Left(e), leftover))
  }

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    charset: Charset,
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, Error, Byte, Chunk[String]] = {
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
  )(implicit trace: ZIOTrace): ZPipeline[Any, Error, Byte, String] = {
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
            ZIO.fail(Error.LineTooLong(options.maximumLineLength))
              .when(s.leftover.length > options.maximumLineLength)
          } *> stateRef.modify(splitBytes[Chunk](bytes.toArray, _).swap)
      }
    }
  }
}
