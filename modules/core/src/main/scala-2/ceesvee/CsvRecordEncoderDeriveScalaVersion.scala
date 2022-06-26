package ceesvee

import shapeless.::
import shapeless.Generic
import shapeless.HList
import shapeless.HNil

trait CsvRecordEncoderDeriveScalaVersion {

  implicit val hnil: CsvRecordEncoderDerive[HNil] = {
    CsvRecordEncoderDerive.instance(_ => Iterable.empty)
  }

  implicit def hcons[H, T <: HList](implicit
    H: => CsvRecordEncoder[H],
    T: => CsvRecordEncoderDerive[T],
  ): CsvRecordEncoderDerive[H :: T] = {
    CsvRecordEncoderDerive.instance { case h :: t =>
      H.encode(h) ++ T.encode(t)
    }
  }

  implicit def generic[T, G](implicit
    gen: Generic.Aux[T, G],
    encoder: => CsvRecordEncoderDerive[G],
  ): CsvRecordEncoderDerive[T] = {
    CsvRecordEncoderDerive.instance { t =>
      encoder.encode(gen.to(t))
    }
  }
}
