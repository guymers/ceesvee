package ceesvee

import cats.effect.IO
import zio.ZIO

package object tests {

  def catsIoToZio[A](io: IO[A]) = {
    import cats.effect.unsafe.implicits.global

    ZIO.effectAsyncInterrupt[Any, Throwable, A] { cb =>
      val (future, cancel) = io.unsafeToFutureCancelable()
      cb(ZIO.fromFuture(_ => future))
      Left(ZIO.fromFuture(_ => cancel()).orDie)
    }
  }
}
