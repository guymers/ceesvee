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
import ceesvee.CsvReader

object ZioCsvParser {
  import CsvParser.State
  import CsvParser.ignoreLine
  import CsvParser.parseLine
  import CsvParser.splitStrings

  /**
   * Turns a stream of strings into a stream of CSV records extracting the first
   * record.
   */
  def parseWithHeader[R, E](
    stream: ZStream[R, E, String],
    options: CsvReader.Options,
  )(implicit
    trace: ZIOTrace,
  ): ZIO[Scope & R, Error[E], (Chunk[String], ZStream[Any, Error[E], Chunk[String]])] = {
    stream.mapError(Left(_)).peel {
      extractFirstLine(options).mapError(Right(_))
    }.map { case ((headers, state, records), s) =>
      (headers, ZStream.fromChunk(records) ++ (s >>> _parse(state, options).mapError(Right(_))))
    }
  }

  private def extractFirstLine(options: CsvReader.Options)(implicit trace: ZIOTrace) = {
    def process(state: State, strings: Chunk[String]) = {
      val (newState, lines) = splitStrings(strings, state)
      val records = lines.filter(str => !ignoreLine(str, options)).map(parseLine[Chunk](_, options))
      (newState, records)
    }

    extractFirstLine_(State.initial, options)(_.leftover.length, process)
  }

  private[zio] def extractFirstLine_[A, S](initialState: S, options: CsvReader.Options)(
    leftoverLength: S => Int,
    process: (S, Chunk[A]) => (S, Iterable[Chunk[String]]),
  )(implicit trace: ZIOTrace) = {

    val initial: Chunk[Chunk[String]] = Chunk.empty

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def done(state: S, records: Chunk[Chunk[String]]) = {
      NonEmptyChunk.fromChunk(records).map { rs =>
        Push.emit((rs.head, state, rs.tail), Chunk.empty)
      }
    }

    val push = Ref.make((initialState, initial)).map { stateRef => (chunk: Option[Chunk[A]]) =>
      chunk match {
        case None => stateRef.get.flatMap { case (state, lines) =>
            done(state, lines).getOrElse(Push.emit((Chunk.empty, state, lines), Chunk.empty))
          }

        case Some(chunks) =>
          stateRef.get.flatMap { case (state, records) =>
            if (leftoverLength(state) > options.maximumLineLength) {
              Push.fail(CsvParser.Error.LineTooLong(options.maximumLineLength), Chunk.empty)
            } else {
              val (newState, moreRecords) = process(state, chunks)
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
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, String, Chunk[String]] = {
    _parse(State.initial, options)
  }

  private[ceesvee] def _parse(state: State, options: CsvParser.Options)(implicit trace: ZIOTrace) = {
    _splitLines(state, options) >>>
      ZPipeline.filter[String](str => !ignoreLine(str, options)) >>>
      ZPipeline.map(parseLine[Chunk](_, options))
  }

  /**
   * Split strings into CSV lines using both '\n' and '\r\n' as delimiters.
   *
   * Delimiters within double-quotes are ignored.
   */
  def splitLines(
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace): ZPipeline[Any, CsvParser.Error, String, String] = {
    _splitLines(State.initial, options)
  }

  private def _splitLines(
    state: State,
    options: CsvParser.Options,
  )(implicit trace: ZIOTrace) = ZPipeline.fromPush {
    Ref.make(state).map { stateRef => (chunk: Option[Chunk[String]]) =>
      chunk match {
        case None =>
          stateRef.getAndSet(State.initial).map { case State(leftover, _, _) =>
            if (leftover.isEmpty) Chunk.empty else Chunk(leftover)
          }

        case Some(strings) =>
          stateRef.get.flatMap { case State(leftover, _, _) =>
            ZIO.fail(CsvParser.Error.LineTooLong(options.maximumLineLength))
              .when(leftover.length > options.maximumLineLength)
          } *> stateRef.modify(splitStrings(strings, _).swap)
      }
    }
  }
}
