package ceesvee

import scala.deriving.Mirror

trait CsvRecordEncoderDeriveScalaVersion {

  given CsvRecordEncoderDerive[EmptyTuple] = {
    CsvRecordEncoderDerive.instance(_ => Iterable.empty)
  }

  given [H, T <: Tuple](using
    H: => CsvRecordEncoder[H],
    T: => CsvRecordEncoderDerive[T],
  ): CsvRecordEncoderDerive[H *: T] = {
    CsvRecordEncoderDerive.instance { case h *: t =>
      H.encode(h) ++ T.encode(t)
    }
  }

  given [A <: Product, T](using
    m: Mirror.ProductOf[A],
    ev: m.MirroredElemTypes =:= T,
    encoder: => CsvRecordEncoderDerive[T],
  ): CsvRecordEncoderDerive[A] = {
    CsvRecordEncoderDerive.instance { a =>
      encoder.encode(Tuple.fromProductTyped(a))
    }
  }
}
