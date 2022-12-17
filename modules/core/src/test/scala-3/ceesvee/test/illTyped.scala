package ceesvee.test

import scala.compiletime.testing.*

// https://github.com/typelevel/shapeless-3/blob/v3.2.0/modules/test/src/main/scala/shapeless3/test/typechecking.scala
object illTyped {
  inline def apply(code: String): Unit = assert(!typeChecks(code))
  inline def apply(code: String, expected: String): Unit = apply(code)
}
