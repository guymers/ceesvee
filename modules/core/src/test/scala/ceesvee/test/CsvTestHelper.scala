package ceesvee.test

import ceesvee.CsvWriter
import zio.NonEmptyChunk
import zio.test.Gen

object CsvTestHelper {

  object gen {

    def oneOfChar(chars: NonEmptyChunk[Char]) = {
      Gen.oneOf(chars.map(Gen.const(_))*)
    }

    private val limitedAnyString = for {
      max <- Gen.int(0, 64)
      str <- Gen.stringBounded(0, max)(Gen.anyUnicodeChar)
    } yield str

    private val whitespace = Gen.stringBounded(1, 5)(oneOfChar(NonEmptyChunk(' ', '\t')))

    def withLeadingOrTrailingWhitespace(str: String) = Gen.oneOf(
      whitespace.map(_ concat str),
      whitespace.map(str concat _),
      Gen.crossN(whitespace, whitespace) { case (prefix, suffix) => s"$prefix$str$suffix" },
    )

    def injectInMiddle[R](str: String)(gen: Gen[R, Char]) = {
      val (a, z) = str.splitAt(str.length)

      gen.map(c => s"$a$c$z")
    }

    val fieldNoQuoting = limitedAnyString.filter(!CsvWriter.needsQuoting(_))
    val fieldNeedsQuoting = limitedAnyString.flatMap { str =>
      val injected = injectInMiddle(str)(oneOfChar(NonEmptyChunk('"', ',', '\n', '\r')))

      Gen.oneOf(
        withLeadingOrTrailingWhitespace(str),
        injected,
        injected.flatMap(withLeadingOrTrailingWhitespace(_)),
      )
    }.filter(CsvWriter.needsQuoting(_))

    val field = Gen.weighted(
      (fieldNoQuoting, 80),
      (fieldNeedsQuoting, 20),
    )

    val fields = for {
      f <- field
      size <- Gen.int(0, 11)
      fs <- Gen.listOfN(size)(field)
    } yield NonEmptyChunk.fromIterable(f, fs)
  }
}
