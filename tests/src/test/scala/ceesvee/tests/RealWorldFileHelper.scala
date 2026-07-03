package ceesvee.tests

import cats.effect.IO
import cats.syntax.show.*
import zio.ZIO
import zio.stream.ZPipeline
import zio.stream.ZStream

import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import scala.util.Using

object RealWorldFileHelper {

  private val ChunkSize = 16 * 1024

  private def resourceStream(resource: String) = {
    val classLoader = Thread.currentThread().getContextClassLoader
    Option(classLoader.getResourceAsStream(resource))
      .getOrElse(throw new FileNotFoundException(show"Resource not found: $resource"))
  }

  def readResource[T](resource: String)(f: Iterator[String] => T) = {
    Using.resource(resourceStream(resource)) { is =>
      f(strings(is, StandardCharsets.UTF_8))
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

  def readResourceFs2(resource: String) = {
    fs2.io.readClassLoaderResource[IO](resource, chunkSize = ChunkSize).through(fs2.text.utf8.decode)
  }

  def readResourceZio(resource: String) = {
    ZStream.fromInputStreamZIO(ZIO.attemptBlockingIO(resourceStream(resource)), chunkSize = ChunkSize) >>>
      ZPipeline.utfDecode
  }
}
