package ceesvee.tests

import cats.effect.IO
import zio.stream.ZPipeline
import zio.stream.ZStream

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object RealWorldFileHelper {

  def readFile[T](path: Path)(f: Iterator[String] => T) = {
    val is = Files.newInputStream(path)
    try {
      f(strings(is, StandardCharsets.UTF_8))
    } finally {
      is.close()
    }
  }

  private val UTF8BOM = List(0xef, 0xbb, 0xbf).map(_.toByte)

  private def strings(is: InputStream, cs: Charset) = new Iterator[String] {
    private var initial = true
    private var read = 0
    private val buffer = Array.ofDim[Byte](16384)

    override def hasNext = {
      if (read == 0) {
        read = is.read(buffer)
      }
      read >= 0
    }

    override def next() = {
      if (read < 0) {
        throw new NoSuchElementException
      } else {
        val _bytes = buffer.take(read)
        read = 0

        val bytes = if (initial) {
          initial = false
          if (_bytes.take(3).toList == UTF8BOM) _bytes.drop(3) else _bytes
        } else _bytes
        new String(bytes, cs)
      }
    }
  }

  def readFileFs2(path: Path) = {
    fs2.io.file.Files[IO].readAll(fs2.io.file.Path.fromNioPath(path)).through(fs2.text.utf8.decode)
  }

  def readFileZio(path: Path) = {
    ZStream.fromPath(path, chunkSize = 16384) >>> ZPipeline.utfDecode
  }
}
