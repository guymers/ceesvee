package ceesvee.zio

import _root_.zio.Trace as ZIOTrace
import _root_.zio.stream.ZStream
import ceesvee.CsvRecordEncoder
import ceesvee.CsvWriter

// overloading keeps the existing methods while adding options taking variants
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object ZioCsvWriter {
  import CsvWriter.fieldsToLine

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[R, E, A](
    header: Iterable[String],
    rows: ZStream[R, E, A],
  )(using CsvRecordEncoder[A], ZIOTrace): ZStream[R, E, String] = {
    encodeWithHeader(header, rows, CsvWriter.Options.Defaults)
  }

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[R, E, A](
    header: Iterable[String],
    rows: ZStream[R, E, A],
    options: CsvWriter.Options,
  )(using CsvRecordEncoder[A], ZIOTrace): ZStream[R, E, String] = {
    ZStream.succeed(fieldsToLine(header, options)) ++ encode(rows, options)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[R, E, A](
    rows: ZStream[R, E, A],
  )(using encoder: CsvRecordEncoder[A], trace: ZIOTrace): ZStream[R, E, String] = {
    encode(rows, CsvWriter.Options.Defaults)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[R, E, A](
    rows: ZStream[R, E, A],
    options: CsvWriter.Options,
  )(using encoder: CsvRecordEncoder[A], trace: ZIOTrace): ZStream[R, E, String] = {
    rows.map(encoder.encode(_)).map(fieldsToLine(_, options))
  }
}
