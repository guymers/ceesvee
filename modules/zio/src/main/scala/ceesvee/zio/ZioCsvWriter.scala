package ceesvee.zio

import _root_.zio.Trace
import _root_.zio.stream.ZStream
import ceesvee.CsvRecordEncoder

object ZioCsvWriter {
  import ceesvee.CsvWriter.fieldsToLine

  /**
   * Encode rows into CSV lines prepending the given header.
   */
  def encodeWithHeader[R, E, A](
    header: Iterable[String],
    rows: ZStream[R, E, A],
  )(implicit E: CsvRecordEncoder[A], trace: Trace): ZStream[R, E, String] = {
    ZStream.succeed(fieldsToLine(header)) ++ encode(rows)
  }

  /**
   * Encode rows into CSV lines.
   */
  def encode[R, E, A](
    rows: ZStream[R, E, A],
  )(implicit E: CsvRecordEncoder[A], trace: Trace): ZStream[R, E, String] = {
    rows.map(E.encode(_)).map(fieldsToLine(_))
  }
}
