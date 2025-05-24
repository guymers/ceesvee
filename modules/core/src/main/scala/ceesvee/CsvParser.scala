package ceesvee

import java.nio.charset.Charset
import jdk.incubator.vector.ByteVector
import scala.annotation.switch
import scala.annotation.tailrec
import scala.collection.Factory
import scala.collection.mutable

@SuppressWarnings(Array(
  "org.wartremover.warts.MutableDataStructures",
  "org.wartremover.warts.Throw",
  "org.wartremover.warts.Var",
  "org.wartremover.warts.While",
))
object CsvParser {

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
    in: Iterator[Array[Byte]],
    options: Options,
    charset: Charset,
  )(implicit f: Factory[String, C[String]]): Iterator[C[String]] = {
    splitLinesVector(in, options)
      .map(bytes => new String(bytes, charset))
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
  def splitLines(in: Iterator[String], options: Options): Iterator[String] = new SplitLinesIterator(in, options)
  private final class SplitLinesIterator(in: Iterator[String], options: Options) extends Iterator[String] {
    private val toOutput = mutable.Queue.empty[String]
    private var state = State.initial

    override def hasNext = toOutput.nonEmpty || in.hasNext || state.leftover.nonEmpty

    @tailrec override def next() = {
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

  /**
   * Splits the given byte arrays into CSV lines using the Vector API by
   * splitting on either '\r\n' or '\n'.
   *
   * '"' is the only valid escape for nested double quotes.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def splitLinesVector(in: Iterator[Array[Byte]], options: Options): Iterator[Array[Byte]] = new SplitLinesVectorIterator(in, options)
  private final class SplitLinesVectorIterator(in: Iterator[Array[Byte]], options: Options) extends Iterator[Array[Byte]] {
    private val toOutput = mutable.Queue.empty[Array[Byte]]
    private var state = StateBytes.initial

    override def hasNext = toOutput.nonEmpty || in.hasNext || state.leftover.nonEmpty

    @tailrec override def next() = {
      if (toOutput.nonEmpty) {
        toOutput.dequeue()
      } else {
        val leftover = state.leftover
        if (leftover.length > options.maximumLineLength) {
          throw Error.LineTooLong(options.maximumLineLength)
        }

        if (!in.hasNext) {
          state = StateBytes.initial
          leftover
        } else {
          val bytes = in.next()
          val (newState, lines) = splitBytesIntoLinesVector(bytes, state)
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

  class StateBytes(
    val leftover: Array[Byte],
    val insideQuote: Boolean,
  )
  object StateBytes {
    val initial: StateBytes = new StateBytes(
      leftover = Array.emptyByteArray,
      insideQuote = false,
    )
  }

  private val Quote: Byte = '"'
  private val NewLine: Byte = '\n'
  private val CarriageReturn: Byte = '\r'

  private val ByteVectorSpecies = ByteVector.SPECIES_PREFERRED

  def splitBytesIntoLinesVector[C[S] <: Iterable[S]](
    bytes: Array[Byte],
    state: StateBytes,
  )(implicit f: Factory[Array[Byte], C[Array[Byte]]]): (StateBytes, C[Array[Byte]]) = {

    val builder = f.newBuilder
    var insideQuote = state.insideQuote
    var sliceStart = 0
    var prevNLChars = 0L

    val loopBound = ByteVectorSpecies.loopBound(bytes.length)

    var i = 0
    while (i < bytes.length) {
      val vector = if (i >= loopBound) {
        val m = ByteVectorSpecies.indexInRange(i, bytes.length)
        ByteVector.fromArray(ByteVectorSpecies, bytes, i, m)
      } else {
        ByteVector.fromArray(ByteVectorSpecies, bytes, i)
      }

      val quoteChars = vector.eq(Quote).toLong

      // set all bits between quotes
      var quoteMask = quoteChars
      var quoteStart = if (insideQuote) 0 else -1
      var quoteMaskBitsSet = quoteMask
      while (quoteMaskBitsSet > 0) {
        val r = java.lang.Long.numberOfTrailingZeros(quoteMaskBitsSet)
        quoteMaskBitsSet = quoteMaskBitsSet ^ java.lang.Long.lowestOneBit(quoteMaskBitsSet)

        if (quoteStart >= 0) {
          var j = r - 1
          while (j >= quoteStart) {
            quoteMask = quoteMask | (1L << j)
            j = j - 1
          }
          quoteStart = -1
        } else {
          quoteStart = r
        }
      }
      if (quoteStart >= 0) {
        var j = ByteVectorSpecies.length - 1
        while (j > quoteStart) {
          quoteMask = quoteMask | (1L << j)
          j = j - 1
        }
      }
      insideQuote = java.lang.Long.highestOneBit(quoteMask) == Long.MinValue

//          println(string.substring(i, (i + ByteVectorSpecies.length).min(string.length)))
//          println(String.format("%64s", java.lang.Long.toBinaryString(quotesMask)).replace(' ', '0'))
//          println(("insideQuote", insideQuote, java.lang.Long.lowestOneBit(quotesMask), java.lang.Long.highestOneBit(quotesMask)))

      val crChars = vector.eq(CarriageReturn).toLong
      val nlChars = vector.eq(NewLine).toLong
      val crnlChars = crChars | nlChars
      val crIgnoringWithinQuotes = crChars & ~(crChars & quoteMask)

      val crnlIgnoringWithinQuotes = crnlChars & ~(crnlChars & quoteMask)
      val crCharsBeforeNl = crIgnoringWithinQuotes & ((nlChars >> 1) | (prevNLChars << 63))
      prevNLChars = nlChars

      /* \ = \r, | = \n
          a\|b\c|"d\|e"|f
          000000010000100 = quoteChars
          000000011111100 = quoteMask
          010010000100000 = crChars
          001000100010010 = nlChars
          011010100110010 = crnlChars
          010010000000000 = crIgnoringWithinQuotes
          011010100000010 = crnlIgnoringWithinQuotes
          010001000100100 = nlChars << 1
          010000000000000 = crCharsBeforeNl
       */

      var crnlIgnoringWithinQuotesBitsSet = crnlIgnoringWithinQuotes
      while (crnlIgnoringWithinQuotesBitsSet > 0) {
        val r = java.lang.Long.numberOfTrailingZeros(crnlIgnoringWithinQuotesBitsSet)
        crnlIgnoringWithinQuotesBitsSet = crnlIgnoringWithinQuotesBitsSet ^ java.lang.Long.lowestOneBit(crnlIgnoringWithinQuotesBitsSet)

        val sliceTo = i + r
        val leftover = if (sliceStart == 0) state.leftover else Array.emptyByteArray
        val _ = builder += leftover ++ arraySlice(bytes, sliceStart, sliceTo, i, crCharsBeforeNl)

        sliceStart = sliceTo + 1
      }

      i = i + ByteVectorSpecies.length
    }

    val leftover = if (sliceStart == 0) {
      state.leftover ++ bytes
    } else {
      bytes.slice(sliceStart, bytes.length)
    }

    (new StateBytes(leftover, insideQuote = insideQuote), builder.result())
  }

  /**
   * Parse a line into a collection of CSV fields.
   */
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

  // https://lemire.me/blog/2018/02/21/iterating-over-set-bits-quickly/
  private def bitsSet(l: Long): Iterator[Int] = new BitsSetIterator(l)
  private final class BitsSetIterator(long: Long) extends Iterator[Int] {
    private var l = long

    override def hasNext = l > 0
    override def next() = {
      val i = java.lang.Long.numberOfTrailingZeros(l)
      l = l ^ java.lang.Long.lowestOneBit(l)
      i
    }
  }

  @SuppressWarnings(Array(
    "org.wartremover.warts.StringPlusAny",
  )) // TODO remove
  private def arraySlice(src: Array[Byte], from: Int, to: Int, offset: Int, ignore: Long) = {
    var from_ = from
    var to_ = to
    var ignoreCount = 0

    var ignoreBitsSet = ignore
    while (ignoreBitsSet > 0) {
      val i = java.lang.Long.numberOfTrailingZeros(ignoreBitsSet) + offset
      ignoreBitsSet = ignoreBitsSet ^ java.lang.Long.lowestOneBit(ignoreBitsSet)

      if (i < from_ || i > to_) {
        ()
      } else if (i == from_) {
        from_ = from_ + 1
      } else if (i == to_) {
        to_ = to_ - 1
      } else {
        ignoreCount = ignoreCount + 1
      }
    }

    val size = to_ - from_ + ignoreCount
    if (size <= 0 || from_ >= to_) {
      Array.emptyByteArray
    } else {
      val dest = Array.ofDim[Byte](size)

      if (ignoreCount == 0) {
//        println(s"1 System.arraycopy(${from_},  0, $size)")
        System.arraycopy(src, from_, dest, 0, size)
      } else {
        var srcPosition = from_
        var destPosition = 0

        var ignoreBitsSet2 = ignore
        while (ignoreBitsSet2 > 0) {
          val i = java.lang.Long.numberOfTrailingZeros(ignoreBitsSet2) + offset
          ignoreBitsSet2 = ignoreBitsSet2 ^ java.lang.Long.lowestOneBit(ignoreBitsSet2)

          if (i < from_ || i > to_) {
            ()
          } else if (srcPosition == i) {
            srcPosition = i + 1
          } else {
            val positionSize = srcPosition - i
//            println(s"2 System.arraycopy(${srcPosition},  $destPosition, $positionSize)")
            System.arraycopy(src, srcPosition, dest, destPosition, positionSize)
            srcPosition = i + 1
            destPosition = destPosition + positionSize
          }
        }
        if (srcPosition < to_) {
//          println(s"3 System.arraycopy(${srcPosition},  $destPosition, ${to_ - srcPosition})")
          System.arraycopy(src, srcPosition, dest, destPosition, to_ - srcPosition)
        }
      }

      dest
    }
  }
}
