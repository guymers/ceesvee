/*
 * Copyright (c) 2013-19 Miles Sabin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ceesvee.test

import scala.compiletime.testing.*

// https://github.com/typelevel/shapeless-3/blob/v3.2.0/modules/test/src/main/scala/shapeless3/test/typechecking.scala
object illTyped {
  inline def apply(code: String): Unit = assert(!typeChecks(code))
  inline def apply(code: String, expected: String): Unit = apply(code)
}
