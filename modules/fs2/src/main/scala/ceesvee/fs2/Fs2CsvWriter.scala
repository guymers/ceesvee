package ceesvee.fs2

import _root_.fs2.Stream
import ceesvee.CsvRecordEncoder

object Fs2CsvWriter {
  import ceesvee.CsvWriter.fieldsToLine

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[F[_], A](
    header: Iterable[String],
    rows: Stream[F, A],
  )(implicit E: CsvRecordEncoder[A]): Stream[F, String] = {
    Stream.emit(fieldsToLine(header)) ++ encode(rows)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[F[_], A](
    rows: Stream[F, A],
  )(implicit E: CsvRecordEncoder[A]): Stream[F, String] = {
    rows.map(E.encode(_)).map(fieldsToLine(_))
  }
}
