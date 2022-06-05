package ceesvee

import scala.deriving.Mirror

trait CsvRecordDecoderDeriveScalaVersion {

  given CsvRecordDecoderDerive[EmptyTuple] = {
    new CsvRecordDecoderDerive(Nil, _ => EmptyTuple)
  }

  given [H, T <: Tuple](using
    H: => CsvRecordDecoder[H],
    T: => CsvRecordDecoderDerive[T],
  ): CsvRecordDecoderDerive[H *: T] = {
    new CsvRecordDecoderDerive[H *: T](
      H.decoders ::: T.decoders,
      values => {
        val (h, t) = values.splitAt(H.decoders.length)
        H.lift(h) *: T.lift(t)
      },
    )
  }

  given [P <: Product, A](using
    m: Mirror.ProductOf[P],
    ev: A =:= m.MirroredElemTypes,
    decoder: => CsvRecordDecoderDerive[A],
  ): CsvRecordDecoderDerive[P] = {
    new CsvRecordDecoderDerive[P](
      decoder.decoders,
      values => m.fromProduct(ev(decoder.lift(values))),
    )
  }

}
