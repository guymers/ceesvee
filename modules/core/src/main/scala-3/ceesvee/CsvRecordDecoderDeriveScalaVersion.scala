package ceesvee

import scala.deriving.Mirror

@SuppressWarnings(Array("org.wartremover.warts.ImplicitParameter"))
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

  given [A <: Product, T](using
    m: Mirror.ProductOf[A],
    ev: T =:= m.MirroredElemTypes,
    decoder: => CsvRecordDecoderDerive[T],
  ): CsvRecordDecoderDerive[A] = {
    new CsvRecordDecoderDerive[A](
      decoder.decoders,
      values => m.fromProduct(ev(decoder.lift(values))),
    )
  }

}
