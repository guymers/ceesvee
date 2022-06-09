package ceesvee

import zio.Chunk
import zio.NonEmptyChunk
import zio.Ref
import zio.ZIO
import zio.ZManaged
import zio.stream.ZSink
import zio.stream.ZStream
import zio.stream.ZTransducer

object ZioCsvParser {
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
    options: CsvParser.Options,
  ): ZManaged[R, Either[Either[E, Error], CsvHeader.MissingHeaders], ZStream[R, Either[E, Error], Either[CsvRecordDecoder.Error, T]]] = {
    for {
      tuple <- stream.mapError(Left(_)).peel {
        extractFirstLine(maximumLineLength = options.maximumLineLength).mapError(Right(_))
      }.mapError(Left(_))
      ((headerFields, state, records), s) = tuple
      decoder <- ZIO.fromEither(header.create(headerFields)).mapError(Right(_)).toManaged_
      transducer = _parse(state, options)
    } yield {
      (
        ZStream.fromChunk(records) ++
          s.transduce(transducer.mapError(Right(_)))
      ).map(decoder.decode(_))
    }
  }

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   */
  def decode[T](
    options: CsvParser.Options,
  )(implicit D: CsvRecordDecoder[T]): ZTransducer[Any, Error, String, Either[CsvRecordDecoder.Error, T]] = {
    parse(options).map(D.decode(_))
  }

  /**
   * Turns a stream of strings into a stream of CSV records.
   */
  def parse(
    options: CsvParser.Options,
  ): ZTransducer[Any, Error, String, Chunk[String]] = {
    _parse(State.initial, options)
  }

  private def _parse(state: State, options: CsvParser.Options) = {
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
    options: CsvParser.Options,
  ): ZTransducer[Any, Error, String, String] = {
    _splitLines(State.initial, options)
  }

  private def _splitLines(
    state: State,
    options: CsvParser.Options,
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

  private def extractFirstLine(
    maximumLineLength: Int,
  ): ZSink[Any, Error, String, String, (Chunk[String], State, Chunk[Chunk[String]])] = {
    import ZSink.Push

    val initial: Chunk[Chunk[String]] = Chunk.empty

    @SuppressWarnings(Array("org.wartremover.warts.TraversableOps"))
    def done(state: State, records: Chunk[Chunk[String]]) = {
      NonEmptyChunk.fromChunk(records).map { rs =>
        Push.emit((rs.head, state, rs.tail), Chunk.empty)
      }
    }

    ZSink {
      Ref.makeManaged((State.initial, initial)).map { stateRef =>
        {
          case None => stateRef.get.flatMap { case (state, lines) =>
              done(state, lines).getOrElse(Push.emit((Chunk.empty, state, lines), Chunk.empty))
            }

          case Some(strings: Chunk[String]) =>
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
    }
  }
}
