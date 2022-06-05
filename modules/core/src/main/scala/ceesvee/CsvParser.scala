package ceesvee

import scala.annotation.switch
import scala.annotation.tailrec
import scala.collection.Factory
import scala.collection.mutable

object CsvParser {

  case class Options(
    maximumLineLength: Int,
  )

  /**
   * Turns a stream of strings into a stream of decoded CSV records.
   *
   * The given strings must contain new lines as this method splits on them.
   *
   * Blank lines and lines starting with '#' are ignored.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def decode[T](
    in: Iterator[String],
    options: Options,
  )(implicit D: CsvRecordDecoder[T]): Iterator[Either[CsvRecordDecoder.Error, T]] = {
    parse[List](in, options).map(fields => D.decode(fields))
  }

  /**
   * Parses the given strings into CSV fields.
   *
   * The given strings must contain new lines as this method splits on them.
   *
   * Blank lines and lines starting with '#' are ignored.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def parse[C[_]](
    in: Iterator[String],
    options: Options,
  )(implicit f: Factory[String, C[String]]): Iterator[C[String]] = {
    splitLines(in, options)
      .filter(str => !ignoreLine(str))
      .map(parseLine(_))
  }

  def isBlank(line: String): Boolean = line.isEmpty || line.trim.isEmpty
  def isComment(line: String): Boolean = line.trim.startsWith("#")

  def ignoreLine(line: String): Boolean = isBlank(line) || isComment(line)

  /**
   * Splits the given strings into CSV lines by splitting on either '\r\n' and
   * '\n'.
   *
   * Both '"' and '\' are valid escapes for nested double quotes.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def splitLines(in: Iterator[String], options: Options): Iterator[String] = new Iterator[String] {
    @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
    private val toOutput = mutable.Queue.empty[String]
    @SuppressWarnings(Array("org.wartremover.warts.Var"))
    private var state = State.initial

    override def hasNext: Boolean = toOutput.nonEmpty || in.hasNext || state.leftover.nonEmpty

    @SuppressWarnings(Array("org.wartremover.warts.Throw"))
    @tailrec override def next(): String = {
      if (toOutput.nonEmpty) {
        toOutput.dequeue()
      } else {
        val leftover = state.leftover
        if (leftover.length > options.maximumLineLength) {
          throw Error.LineTooLong(options.maximumLineLength)
        }

        if (!in.hasNext) {
          state = State.initial
          leftover
        } else {
          val str = in.next()
          val (newState, lines) = splitStrings(List(str), state)
          val _ = toOutput.enqueueAll(lines)
          state = newState
          next()
        }
      }
    }
  }

  sealed trait Error
  object Error {
    final case class LineTooLong(maximum: Int) extends RuntimeException(
        s"CSV line exceeded maximum length of ${maximum.toString}",
      ) with Error
  }

  case class State(
    leftover: String,
    insideQuote: Boolean,
  )
  object State {
    val initial: State = State(
      leftover = "",
      insideQuote = false,
    )
  }

  // FIXME allow configuration of escape char?
  @SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.While"))
  def splitStrings[C[S] <: Iterable[S]](
    strings: C[String],
    state: State,
  )(implicit f: Factory[String, C[String]]): (State, C[String]) = {
    val builder = f.newBuilder
    var insideQuote = state.insideQuote
    var leftover = state.leftover

    strings.iterator.filter(_.nonEmpty).foreach { string =>
      val concat = leftover concat string

      // assume we have already processed `leftover`,
      // reprocess the last character in case it was a '\', '"' or '\r'
      var i = (leftover.length - 1).max(0)
      var sliceStart = 0

      while (i < concat.length) {
        (concat(i): @switch) match {

          case '"' =>
            if (insideQuote && (i + 1) < concat.length && concat(i + 1) == '"') { // ""
              i += 2
            } else {
              i += 1
              if (i < concat.length) {
                insideQuote = !insideQuote
              }
            }

          case '\n' =>
            if (!insideQuote) {
              val _ = builder += concat.substring(sliceStart, i)
              i += 1
              sliceStart = i
            } else {
              i += 1
            }

          case '\r' =>
            if (!insideQuote && (i + 1) < concat.length && concat(i + 1) == '\n') {
              val _ = builder += concat.substring(sliceStart, i)
              i += 2
              sliceStart = i
            } else {
              i += 1
            }

          case _ =>
            i += 1
        }
      }

      leftover = concat.substring(sliceStart, concat.length)
    }

    (State(leftover, insideQuote = insideQuote), builder.result())
  }

  /**
   * Parse a line into a collection of CSV fields.
   */
  @SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.While"))
  def parseLine[C[_]](
    line: String,
  )(implicit f: Factory[String, C[String]]): C[String] = {
    val fields = f.newBuilder
    var insideQuote = false

    @SuppressWarnings(Array("org.wartremover.warts.MutableDataStructures"))
    val slices = mutable.ListBuffer.empty[(Int, Int)]
    var sliceStart = 0

    var i = 0

    def processSlices(): Unit = {
      val str = (slices += (sliceStart -> i)).foldLeft("") { case (str, (start, end)) =>
        str concat line.substring(start, end)
      }
      val _ = fields += str.trim.stripPrefix("\"").stripSuffix("\"")
      slices.clear()
    }

    while (i < line.length) {
      (line(i): @switch) match {

        case ',' =>
          if (!insideQuote) {
            processSlices()
            i += 1
            sliceStart = i
          } else {
            i += 1
          }

        case '"' =>
          if (insideQuote && (i + 1) < line.length && line(i + 1) == '"') { // ""
            val _ = slices += (sliceStart -> i)
            sliceStart = i + 1
            i += 2
          } else {
            i += 1
            insideQuote = !insideQuote
          }

        case _ =>
          i += 1
      }
    }

    processSlices()

    fields.result()
  }
}
