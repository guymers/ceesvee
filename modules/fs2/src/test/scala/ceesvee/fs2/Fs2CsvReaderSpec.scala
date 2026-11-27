package ceesvee.fs2

import ceesvee.CsvHeader
import ceesvee.CsvReader
import ceesvee.CsvRecordDecoder
import fs2.Stream
import zio.Task
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object Fs2CsvReaderSpec extends ZIOSpecDefault {
  import zio.interop.catz.asyncInstance

  private val options = CsvReader.Options.Defaults

  private val decodeWithHeaderSuite = suite("decode with header")(
    test("empty input") {
      decode(Stream.empty[Task], Test.header).either.map { result =>
        assertTrue(result == Left(CsvHeader.MissingHeaders(::("a", List("b", "c")))))
      }
    },
    test("only header row") {
      decode(Stream.emit("a,b,c").covary[Task], Test.header).map { result =>
        assertTrue(result == Nil)
      }
    },
    test("header row with trailing newline") {
      decode(Stream.emit("a,b,c\n").covary[Task], Test.header).map { result =>
        assertTrue(result == Nil)
      }
    },
    test("invalid header row") {
      decode(Stream.emit("a,b,d").covary[Task], Test.header).either.map { result =>
        assertTrue(result == Left(CsvHeader.MissingHeaders(::("c", Nil))))
      }
    },
    test("valid") {
      decode(Stream.emit("a,b,c\ns,1,true").covary[Task], Test.header).map { result =>
        assertTrue(result == List(Right(Test("s", 1, true))))
      }
    },
    test("record split across chunks") {
      val stream = (Stream.emit("a,b,c\ns,") ++ Stream.emit("1,true")).covary[Task]
      decode(stream, Test.header).map { result =>
        assertTrue(result == List(Right(Test("s", 1, true))))
      }
    },
    test("can be run multiple times") {
      val stream = Stream.emit("a,b,c\ns,1,true").covary[Task]
      for {
        result1 <- decode(stream, Test.header)
        result2 <- decode(stream, Test.header)
      } yield {
        assertTrue(result1 == List(Right(Test("s", 1, true)))) &&
        assertTrue(result1 == result2)
      }
    },
  )

  override val spec = suite("Fs2CsvReader")(
    decodeWithHeaderSuite,
  )

  case class Test(
    a: String,
    b: Int,
    c: Boolean,
  ) derives CsvRecordDecoder
  object Test {
    val header: CsvHeader[Test] = CsvHeader.create(::("a", List("b", "c")))
  }

  private def decode[A](stream: Stream[Task, String], header: CsvHeader[A]) = {
    stream.through(Fs2CsvReader.decodeWithHeader(header, options)).compile.toList
  }
}
