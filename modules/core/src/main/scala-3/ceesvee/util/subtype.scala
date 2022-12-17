package ceesvee.util

import scala.annotation.implicitNotFound
import scala.util.NotGiven

@implicitNotFound("${A} must not be a subtype of ${B}")
trait <:!<[A, B] extends Serializable
object <:!< {
  implicit def nsub[A, B](implicit ev: NotGiven[A <:< B]): A <:!< B = new <:!<[A, B] {}
}
