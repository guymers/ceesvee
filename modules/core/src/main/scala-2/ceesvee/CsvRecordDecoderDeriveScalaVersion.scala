package ceesvee

import shapeless.::
import shapeless.Generic
import shapeless.HList
import shapeless.HNil

trait CsvRecordDecoderDeriveScalaVersion {

  implicit val hnil: CsvRecordDecoderDerive[HNil] = {
    new CsvRecordDecoderDerive(Nil, _ => HNil)
  }

  implicit def hcons[H, T <: HList](implicit
    H: => CsvRecordDecoder[H],
    T: => CsvRecordDecoderDerive[T],
  ): CsvRecordDecoderDerive[H :: T] = {
    new CsvRecordDecoderDerive[H :: T](
      H.decoders ::: T.decoders,
      values => {
        val (h, t) = values.splitAt(H.decoders.length)
        H.lift(h) :: T.lift(t)
      },
    )
  }

  implicit def generic[T, G](implicit
    gen: Generic.Aux[T, G],
    decoder: => CsvRecordDecoderDerive[G],
  ): CsvRecordDecoderDerive[T] = {
    new CsvRecordDecoderDerive[T](
      decoder.decoders,
      values => gen.from(decoder.lift(values)),
    )
  }
}
