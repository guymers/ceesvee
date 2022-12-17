package ceesvee.util

import scala.annotation.implicitNotFound

// from https://github.com/milessabin/shapeless/blob/v2.3.10/core/src/main/scala/shapeless/package.scala#L47
@implicitNotFound("${A} must not be a subtype of ${B}")
trait <:!<[A, B] extends Serializable
object <:!< {
  implicit def nsub[A, B]: A <:!< B = new <:!<[A, B] {}
  implicit def nsubAmbig1[A, B >: A]: A <:!< B = sys.error("Unexpected invocation")
  implicit def nsubAmbig2[A, B >: A]: A <:!< B = sys.error("Unexpected invocation")
}
