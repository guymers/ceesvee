package ceesvee.tests

import cats.effect.IO
import cats.syntax.show.*
import zio.ZIO
import zio.stream.ZStream

import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InputStreamReader
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

  private val UTF8BOM = '\uFEFF'

  // Reads characters via a decoder so that a multi-byte character split across
  // the underlying byte chunks is not corrupted.
  private def strings(is: InputStream, cs: Charset) = new Iterator[String] {
    private val reader = new InputStreamReader(is, cs)
    private val buffer = Array.ofDim[Char](16384)
    private var initial = true
    private var read = 0
    private var end = false

    private def fill(): Unit = {
      while (read == 0 && !end) {
        read = reader.read(buffer)

        if (read < 0) {
          read = 0
          end = true
        }
      }
    }

    override def hasNext = {
      fill()
      read > 0
    }

    override def next() = {
      fill()

      if (read == 0) {
        throw new NoSuchElementException
      } else {
        val str = new String(buffer, 0, read)
        read = 0

        if (initial) {
          initial = false
          if (str.nonEmpty && str.charAt(0) == UTF8BOM) str.drop(1) else str
        } else str
      }
    }
  }

  def readResourceFs2(resource: String) = {
    fs2.io.readClassLoaderResource[IO](resource, chunkSize = ChunkSize)
  }

  def readResourceZio(resource: String) = {
    ZStream.fromInputStreamZIO(ZIO.attemptBlockingIO(resourceStream(resource)), chunkSize = ChunkSize)
  }
}
