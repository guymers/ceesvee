package ceesvee

import jdk.incubator.vector.ByteVector

import java.nio.charset.StandardCharsets
import scala.annotation.switch
import scala.annotation.tailrec
import scala.collection.Factory
import scala.collection.mutable

object CsvParser {

  private val VectorAPIAvailable = ModuleLayer.boot().findModule("jdk.incubator.vector").isPresent

  trait Options {
    def commentPrefix: Option[String]
    def maximumLineLength: Int
    def skipBlankRows: Boolean
    def trim: Options.Trim
  }
  object Options {

    val Defaults: Impl = apply(
      commentPrefix = None,
      maximumLineLength = 10_000,
      skipBlankRows = false,
      trim = Trim.True,
    )

    case class Impl(
      commentPrefix: Option[String],
      maximumLineLength: Int,
      skipBlankRows: Boolean,
      trim: Trim,
    ) extends Options

    def apply(
      commentPrefix: Option[String],
      maximumLineLength: Int,
      skipBlankRows: Boolean,
      trim: Trim,
    ): Impl = Impl(
      commentPrefix = commentPrefix,
      maximumLineLength = maximumLineLength,
      skipBlankRows = skipBlankRows,
      trim = trim,
    )

    sealed abstract class Trim {
      def strip(str: String): String
    }
    object Trim {
      case object True extends Trim {
        override def strip(str: String) = str.strip
      }

      case object False extends Trim {
        override def strip(str: String) = str
      }

      case object Start extends Trim {
        override def strip(str: String) = str.stripLeading
      }

      case object End extends Trim {
        override def strip(str: String) = str.stripTrailing
      }
    }
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
      .filter(str => !ignoreLine(str, options))
      .map(parseLine(_, options))
  }

  /**
   * @see
   *   [[parse]]
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def parseVector[C[_]](
    in: Iterator[String],
    options: Options,
  )(implicit f: Factory[String, C[String]]): Iterator[C[String]] = {
    splitLinesVector(in, options)
      .filter(str => !ignoreLine(str, options))
      .map(parseLine(_, options))
  }

  def ignoreLine(line: String, options: Options): Boolean = {
    val l = options.trim.strip(line)

    def isBlank = options.skipBlankRows && l.isEmpty
    def isComment = options.commentPrefix.filter(_.nonEmpty).exists(l.startsWith(_))

    isBlank || isComment
  }

  /**
   * Splits the given strings into CSV lines by splitting on either '\r\n' or
   * '\n'.
   *
   * '"' is the only valid escape for nested double quotes.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def splitLines(in: Iterator[String], options: Options): Iterator[String] = splitLines_(in, options, vector = false)

  def splitLinesVector(in: Iterator[String], options: Options): Iterator[String] = splitLines_(in, options, vector = true)

  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Throw",
    "org.wartremover.warts.Var",
  ))
  private def splitLines_(in: Iterator[String], options: Options, vector: Boolean): Iterator[String] = new Iterator[String] {
    private val toOutput = mutable.Queue.empty[String]
    private var state = State.initial

    private val split = if (vector) splitStringsVector[List](_, _) else splitStrings[List](_, _)

    override def hasNext: Boolean = toOutput.nonEmpty || in.hasNext || state.leftover.nonEmpty

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
          val (newState, lines) = split(List(str), state)
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

  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  def splitStrings[C[S] <: Iterable[S]](
    strings: C[String],
    state: State,
  )(implicit f: Factory[String, C[String]]): (State, C[String]) = {
    val builder = f.newBuilder
    var insideQuote = state.insideQuote
    var leftover = state.leftover

    val it = strings.iterator
    while (it.hasNext) {
      val string = it.next()
      if (string.nonEmpty) {

        val concat = leftover.concat(string)

        // assume we have already processed `leftover`,
        // reprocess the last character in case it was a '\', '"' or '\r'
        var i = (leftover.length - 1).max(0)
        var sliceStart = 0

        while (i < concat.length) {
          (concat(i): @switch) match {

            case '"' =>
              if (insideQuote && (i + 1) < concat.length && concat(i + 1) == '"') { // escaped quote
                i += 2
              } else {
                i += 1
                if (i < concat.length) {
                  insideQuote = !insideQuote
                }
              }

//            case '\\' =>
//              if (insideQuote && (i + 1) < concat.length && concat(i + 1) == '"') { // escaped quote
//                i += 2
//              } else {
//                i += 1
//              }

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
    }

    (State(leftover, insideQuote = insideQuote), builder.result())
  }

  val Quote: Byte = '"'
  val NewLine: Byte = '\n'
  val CarriageReturn: Byte = '\r'

  private val ByteVectorSpecies = ByteVector.SPECIES_PREFERRED

  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  def splitStringsVector[C[S] <: Iterable[S]](
    strings: C[String],
    state: State,
  )(implicit f: Factory[String, C[String]]): (State, C[String]) = {

    val builder = f.newBuilder
    var insideQuote = state.insideQuote
    var leftover = state.leftover

    val it = strings.iterator
    while (it.hasNext) {
      val string = it.next()
      if (string.nonEmpty) {

//        val concat = leftover.concat(string)

//        var i = (leftover.length - 1).max(0)
        var sliceStart = 0

        val bytes = string.getBytes(StandardCharsets.UTF_8)
        val loopBound = ByteVectorSpecies.loopBound(bytes.length)

        var i = 0
        while (i < bytes.length) {
          val vector = if (i >= loopBound) {
            val m = ByteVectorSpecies.indexInRange(i, bytes.length)
            ByteVector.fromArray(ByteVectorSpecies, bytes, i, m)
          } else {
            ByteVector.fromArray(ByteVectorSpecies, bytes, i)
          }

          var quotesMask = vector.eq(Quote).toLong

          var inQuoteStart = if (insideQuote) 0 else -1
          var temp_ = quotesMask
          while (temp_ != 0) { // https://lemire.me/blog/2018/02/21/iterating-over-set-bits-quickly/
            val r = java.lang.Long.numberOfTrailingZeros(temp_)
            if (inQuoteStart >= 0) {
              var j = r - 1
              while (j > inQuoteStart) {
                quotesMask = quotesMask | (1L << j)
                j = j - 1
              }
              inQuoteStart = -1
            } else {
              inQuoteStart = r
            }
            temp_ = temp_ ^ java.lang.Long.lowestOneBit(temp_)
          }
          if (inQuoteStart >= 0) {
            var j = ByteVectorSpecies.length - 1
            while (j > inQuoteStart) {
              quotesMask = quotesMask | (1L << j)
              j = j - 1
            }
          }

//          println(string.substring(i, (i + ByteVectorSpecies.length).min(string.length)))
//          println(String.format("%64s", java.lang.Long.toBinaryString(quotesMask)).replace(' ', '0'))
          insideQuote = java.lang.Long.highestOneBit(quotesMask) == Long.MinValue
//          println(("insideQuote", insideQuote, java.lang.Long.lowestOneBit(quotesMask), java.lang.Long.highestOneBit(quotesMask)))

          val newlinesMask = vector.eq(NewLine)
          val carriageReturnsMask = vector.eq(CarriageReturn)
          val newlinesMask2 = newlinesMask.toLong | carriageReturnsMask.toLong
          val newlinesIgnoringWithQuotes = newlinesMask2 & ~(newlinesMask2 & quotesMask)

          var temp2_ = newlinesIgnoringWithQuotes
          while (temp2_ != 0) {
            val r = java.lang.Long.numberOfTrailingZeros(temp2_)
            val rr = i + r
            // TODO use options.skipBlankLines but also need to ignore carriage returns
//            if (sliceStart == rr) {
//              // ignore carriage returns and empty lines
//              ()
//            } else {
            if (sliceStart == 0) {
              val _ = builder += (leftover ++ string.substring(sliceStart, rr))
            } else {
              val _ = builder += string.substring(sliceStart, rr)
            }
//            }
            sliceStart = rr + 1
            temp2_ = temp2_ ^ java.lang.Long.lowestOneBit(temp2_)
          }

          i = i + ByteVectorSpecies.length
        }

        if (sliceStart == 0) {
          leftover = leftover ++ string
        } else {
          leftover = string.substring(sliceStart, string.length)
        }
      }
    }

    (State(leftover, insideQuote = insideQuote), builder.result())
  }

  /**
   * Parse a line into a collection of CSV fields.
   */
  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  def parseLine[C[_]](
    line: String,
    options: Options,
  )(implicit f: Factory[String, C[String]]): C[String] = {
    val fields = f.newBuilder

    object ParseLine {

      private val slices = mutable.ListBuffer.empty[(Int, Int)]
      private var sliceStart = 0

      private var i = 0
      private var insideQuote = false

      def run(): Unit = {
        while (i < line.length) {
          (line(i): @switch) match {

            case ',' =>
              if (!insideQuote) {
                process()
                i += 1
                sliceStart = i
              } else {
                i += 1
              }

            case '"' =>
              if (insideQuote && (i + 1) < line.length && line(i + 1) == '"') { // escaped quote
                val _ = slices += (sliceStart -> i)
                sliceStart = i + 1
                i += 2
              } else {
                i += 1
                insideQuote = !insideQuote
              }

//            case '\\' =>
//              if (insideQuote && (i + 1) < line.length && line(i + 1) == '"') { // escaped quote
//                val _ = slices += (sliceStart -> i)
//                sliceStart = i + 1
//                i += 2
//              } else {
//                i += 1
//              }

            case _ =>
              i += 1
          }
        }

        process()
      }

      private def process(): Unit = {
        val sb = new mutable.StringBuilder
        (slices += (sliceStart -> i)).foreach { case (start, end) =>
          sb append line.substring(start, end)
        }
        @SuppressWarnings(Array("org.wartremover.warts.ToString"))
        val str = sb.toString

        val _ = fields += {
          // always ignore whitespace around a quoted cell
          val trimmed = Options.Trim.True.strip(str)

          if (trimmed.length >= 2 && trimmed.headOption.contains('"') && trimmed.lastOption.contains('"')) {
            trimmed.substring(1, trimmed.length - 1)
          } else {
            options.trim.strip(str)
          }
        }
        slices.clear()
      }
    }
    ParseLine.run()

    fields.result()
  }

}
