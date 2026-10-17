package ceesvee.util

import scala.annotation.implicitNotFound
import scala.util.NotGiven

/**
 * Evidence that `A` is not a subtype of `B`.
 *
 * This is only needed to prevent given resolution from selecting an `Option`
 * instance when `T` is already an `Option`.
 */
@implicitNotFound("${A} must not be a subtype of ${B}")
trait <:!<[A, B] extends Serializable
object <:!< {
  given nsub[A, B](using NotGiven[A <:< B]): (A <:!< B) = new <:!<[A, B] {}
}
