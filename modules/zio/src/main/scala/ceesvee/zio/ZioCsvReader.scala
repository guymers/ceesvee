package ceesvee.zio

import _root_.zio.Chunk
import _root_.zio.NonEmptyChunk
import _root_.zio.Ref
import _root_.zio.ZIO
import _root_.zio.ZManaged
import _root_.zio.stream.ZSink
import _root_.zio.stream.ZStream
import _root_.zio.stream.ZTransducer
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
  ): ZManaged[R, Either[Either[E, Error], CsvHeader.MissingHeaders], ZStream[R, Either[E, Error], Either[CsvRecordDecoder.Error, T]]] = {
    for {
      tuple <- stream.mapError(Left(_)).peel {
        extractFirstLine(maximumLineLength = options.maximumLineLength).mapError(Right(_))
      }.mapError(Left(_))
      ((headerFields, state, records), s) = tuple
      decoder <- ZIO.fromEither(header.create(headerFields)).mapError(Right(_)).toManaged_
      transducer = ZioCsvParser._parse(state, options)
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
    options: CsvReader.Options,
  )(implicit D: CsvRecordDecoder[T]): ZTransducer[Any, Error, String, Either[CsvRecordDecoder.Error, T]] = {
    ZioCsvParser.parse(options).map(D.decode(_))
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
