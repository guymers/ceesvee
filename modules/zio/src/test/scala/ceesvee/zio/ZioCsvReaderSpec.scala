package ceesvee.zio

import ceesvee.CsvHeader
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder
import zio.Chunk
import zio.stream.ZStream
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object ZioCsvReaderSpec extends ZIOSpecDefault {

  private val options = CsvReader.Options.Defaults

  private val decodeWithHeaderSuite = suite("decode with header")(
    test("no rows") {
      ZioCsvReader.decodeWithHeader(ZStream.empty, Test.header, options).runCollect.either.map { result =>
        assertTrue(result == Left(Right(ZioCsvReader.Error.MissingHeaders(::("a", List("b", "c"))))))
      }
    },
    test("only header row") {
      val stream = ZStream.succeed("a,b,c")
      ZioCsvReader.decodeWithHeader(stream, Test.header, options).runCollect.either.map { result =>
        assertTrue(result == Right(Chunk.empty))
      }
    },
    test("invalid header row") {
      val stream = ZStream.succeed("a,b,d")
      ZioCsvReader.decodeWithHeader(stream, Test.header, options).runCollect.either.map { result =>
        assertTrue(result == Left(Right(ZioCsvReader.Error.MissingHeaders(::("c", Nil)))))
      }
    },
    test("valid") {
      val stream = ZStream.succeed("a,b,c\ns,1,true")
      ZioCsvReader.decodeWithHeader(stream, Test.header, options).runCollect.map { result =>
        assertTrue(result == Chunk(Right(Test("s", 1, true))))
      }
    },
    test("can be run multiple times") {
      val stream = ZStream.succeed("a,b,c\ns,1,true")
      val decode = ZioCsvReader.decodeWithHeader(stream, Test.header, options)
      for {
        result1 <- decode.runCollect
        result2 <- decode.runCollect
      } yield {
        assertTrue(result1 == Chunk(Right(Test("s", 1, true)))) &&
        assertTrue(result1 == result2)
      }
    },
  )

  override val spec = suite("ZioCsvReader")(
    decodeWithHeaderSuite,
  )

  case class Test(
    a: String,
    b: Int,
    c: Boolean,
  )
  object Test {
    implicit val decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derived

    val header: CsvHeader[Test] = CsvHeader.create(::("a", List("b", "c")))(decoder)
  }
}
