package ceesvee

import _root_.zio.ZIO
import cats.effect.IO

package object tests {

  def catsIoToZio[A](io: IO[A]) = {
    import cats.effect.unsafe.implicits.global

    ZIO.asyncInterrupt[Any, Throwable, A] { cb =>
      val (future, cancel) = io.unsafeToFutureCancelable()
      cb(ZIO.fromFuture(_ => future))
      Left(ZIO.fromFuture(_ => cancel()).orDie)
    }
  }
}
