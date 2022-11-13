package ceesvee.zio

import _root_.zio.Cause
import _root_.zio.Chunk
import _root_.zio.NonEmptyChunk
import _root_.zio.Ref
import _root_.zio.Scope
import _root_.zio.Trace
import _root_.zio.ZIO
import _root_.zio.stream.ZPipeline
import _root_.zio.stream.ZSink
import _root_.zio.stream.ZStream
import ceesvee.CsvHeader
import ceesvee.CsvParser
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder

object ZioCsvReader {
  import CsvParser.Error
  import CsvParser.State
  import CsvParser.ignoreLine
  import CsvParser.parseLine
  import CsvParser.splitStrings

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * CSV lines are reordered based on the given headers.
   */
  def decodeWithHeader[R, E, T](
    stream: ZStream[R, E, String],
    header: CsvHeader[T],
    options: CsvReader.Options,
  )(implicit
    trace: Trace,
  ): ZIO[Scope & R, Either[Either[E, Error], CsvHeader.MissingHeaders], ZStream[R, Either[E, Error], Either[CsvHeader.Error, T]]] = {
    for {
      tuple <- stream.mapError(Left(_)).peel {
        extractFirstLine(maximumLineLength = options.maximumLineLength).mapError(Right(_))
      }.mapError(Left(_))
      ((headerFields, state, records), s) = tuple
      decoder <- header.create(headerFields) match {
        case Left(error) => ZIO.refailCause(Cause.fail(error)).mapError(Right(_))
        case Right(decoder) => ZIO.succeed(decoder)
      }
      pipeline = ZioCsvParser._parse(state, options)
    } yield {
      (
        ZStream.fromChunk(records) ++ (s >>> pipeline.mapError(Right(_)))
      ).map(decoder.decode(_))
    }
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvReader.Options,
  )(implicit D: CsvRecordDecoder[T], trace: Trace): ZPipeline[Any, Error, String, Either[CsvRecordDecoder.Error, T]] = {
    ZioCsvParser.parse(options) >>> ZPipeline.map(D.decode(_))
  }

  private def extractFirstLine(maximumLineLength: Int)(implicit trace: Trace) = {

    val initial: Chunk[Chunk[String]] = Chunk.empty

    @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
    def done(state: State, records: Chunk[Chunk[String]]) = {
      NonEmptyChunk.fromChunk(records).map { rs =>
        Push.emit((rs.head, state, rs.tail), Chunk.empty)
      }
    }

    val push = Ref.make((State.initial, initial)).map { stateRef => (chunk: Option[Chunk[String]]) =>
      chunk match {
        case None => stateRef.get.flatMap { case (state, lines) =>
            done(state, lines).getOrElse(Push.emit((Chunk.empty, state, lines), Chunk.empty))
          }

        case Some(strings) =>
          stateRef.get.flatMap { case (state, records) =>
            if (state.leftover.length > maximumLineLength) {
              Push.fail(Error.LineTooLong(maximumLineLength), Chunk.empty)
            } else {
              val (newState, lines) = splitStrings(strings, state)
              val moreRecords = lines.filter(str => !ignoreLine(str)).map(parseLine[Chunk](_))
              val _records = records ++ moreRecords
              done(newState, _records).getOrElse(stateRef.set((newState, _records)) *> Push.more)
            }
          }
      }
    }

    ZSink.fromPush(push)
  }

  object Push {
    val more: ZIO[Any, Nothing, Unit] = ZIO.unit
    def emit[I, Z](z: Z, leftover: Chunk[I]): ZIO[Any, (Right[Nothing, Z], Chunk[I]), Nothing] =
      ZIO.refailCause(Cause.fail((Right(z), leftover)))
    def fail[I, E](e: E, leftover: Chunk[I]): ZIO[Any, (Left[E, Nothing], Chunk[I]), Nothing] =
      ZIO.fail((Left(e), leftover))
  }
}
