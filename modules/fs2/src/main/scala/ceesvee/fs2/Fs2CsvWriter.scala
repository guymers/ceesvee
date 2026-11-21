package ceesvee.fs2

import _root_.fs2.Stream
import ceesvee.CsvRecordEncoder
import ceesvee.CsvWriter

// overloading keeps the existing methods while adding options taking variants
@SuppressWarnings(Array("org.wartremover.warts.Overloading"))
object Fs2CsvWriter {
  import CsvWriter.fieldsToLine

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[F[_], A](
    header: Iterable[String],
    rows: Stream[F, A],
  )(using CsvRecordEncoder[A]): Stream[F, String] = {
    encodeWithHeader(header, rows, CsvWriter.Options.Defaults)
  }

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[F[_], A](
    header: Iterable[String],
    rows: Stream[F, A],
    options: CsvWriter.Options,
  )(using CsvRecordEncoder[A]): Stream[F, String] = {
    Stream.emit(fieldsToLine(header, options)) ++ encode(rows, options)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[F[_], A](
    rows: Stream[F, A],
  )(using E: CsvRecordEncoder[A]): Stream[F, String] = {
    encode(rows, CsvWriter.Options.Defaults)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[F[_], A](
    rows: Stream[F, A],
    options: CsvWriter.Options,
  )(using E: CsvRecordEncoder[A]): Stream[F, String] = {
    rows.map(E.encode(_)).map(fieldsToLine(_, options))
  }
}
