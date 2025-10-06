package ceesvee

import java.nio.charset.Charset
import java.util.regex.Pattern
import jdk.incubator.vector.ByteVector
import scala.annotation.tailrec
import scala.collection.Factory
import scala.collection.mutable

@SuppressWarnings(Array(
  "org.wartremover.warts.MutableDataStructures",
  "org.wartremover.warts.Throw",
  "org.wartremover.warts.Var",
  "org.wartremover.warts.While",
))
object CsvParserVector {
  import CsvParser.Error
  import CsvParser.Options
  import CsvParser.ignoreTrimmedLine

  /**
   * @see
   *   [[CsvParser.parse]]
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def parse[C[_]](
    in: Iterator[Array[Byte]],
    charset: Charset,
    options: Options,
  )(implicit f: Factory[String, C[String]]): Iterator[C[String]] = {
    splitLines(in, options)
      .map(parseLine(_, charset, options))
      .filter(fields => fields != null)
  }

  /**
   * Splits the given byte arrays into CSV lines using the Vector API by
   * splitting on either '\r\n' or '\n'.
   *
   * '"' is the only valid escape for nested double quotes.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  private def splitLines(in: Iterator[Array[Byte]], options: Options): Iterator[Array[Byte]] = new SplitLinesVectorIterator(in, options)
  private final class SplitLinesVectorIterator(in: Iterator[Array[Byte]], options: Options) extends Iterator[Array[Byte]] {
    private val toOutput = mutable.Queue.empty[Array[Byte]]
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
          val bytes = in.next()
          val (newState, lines) = splitBytes(bytes, state)
          val _ = toOutput.enqueueAll(lines)
          state = newState
          next()
        }
      }
    }
  }

  private[ceesvee] class State(
    val leftover: Array[Byte],
    val insideQuote: Boolean,
    val prevCarriageReturn: Boolean,
  )
  private[ceesvee] object State {
    val initial: State = new State(
      leftover = Array.emptyByteArray,
      insideQuote = false,
      prevCarriageReturn = false,
    )
  }

  private val Quote: Byte = '"'
  private val Comma: Byte = ','
  private val NewLine: Byte = '\n'
  private val CarriageReturn: Byte = '\r'

  private val ByteVectorSpecies = ByteVector.SPECIES_PREFERRED

  private[ceesvee] def splitBytes[C[S] <: Iterable[S]](
    bytes: Array[Byte],
    state: State,
  )(implicit f: Factory[Array[Byte], C[Array[Byte]]]): (State, C[Array[Byte]]) = {

    val builder = f.newBuilder
    var insideQuote = state.insideQuote
    var prevCarriageReturn = state.prevCarriageReturn
    var sliceStart = 0

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
      // https://lemire.me/blog/2018/02/21/iterating-over-set-bits-quickly/
      var quoteMaskBitsSet = quoteMask
      while (java.lang.Long.bitCount(quoteMaskBitsSet) > 0) {
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
      val numQuoteChars = java.lang.Long.bitCount(quoteChars)
      insideQuote = (numQuoteChars + (if (insideQuote) 1 else 0)) % 2 == 1

      val crChars = vector.eq(CarriageReturn).toLong
      val crIgnoringWithinQuotes = crChars & ~(crChars & quoteMask)
      val nlChars = vector.eq(NewLine).toLong
      val nlIgnoringWithinQuotes = nlChars & ~(nlChars & quoteMask)

      /* \ = \r, | = \n
          a\|b\c|"d\|e"|f
          000000010000100 = quoteChars
          000000011111100 = quoteMask
          010010000100000 = crChars
          010010000000000 = crIgnoringWithinQuotes
          001000100010010 = nlChars
          001000100000010 = nlIgnoringWithinQuotes
       */

      var removeCrNlBitsSet = nlIgnoringWithinQuotes
      while (java.lang.Long.bitCount(removeCrNlBitsSet) > 0) {
        val r = java.lang.Long.numberOfTrailingZeros(removeCrNlBitsSet)
        removeCrNlBitsSet = removeCrNlBitsSet ^ java.lang.Long.lowestOneBit(removeCrNlBitsSet)

        val isPrevCr =
          (r == 0 && prevCarriageReturn) ||
          (crIgnoringWithinQuotes & (1L << (r - 1))) == 1L << (r - 1)

        val sliceTo = i + r
        val leftover = if (sliceStart == 0) state.leftover else Array.emptyByteArray
        val to = if (isPrevCr) sliceTo - 1 else sliceTo
        val _ = builder += leftover ++ arraySlice(bytes, sliceStart, to, i, 0)

        sliceStart = sliceTo + 1
      }

      prevCarriageReturn = java.lang.Long.numberOfLeadingZeros(crIgnoringWithinQuotes) == 0
      i = i + ByteVectorSpecies.length
    }

    val leftover = if (sliceStart == 0) {
      state.leftover ++ bytes
    } else {
      bytes.slice(sliceStart, bytes.length)
    }

    (new State(leftover, insideQuote = insideQuote, prevCarriageReturn = prevCarriageReturn), builder.result())
  }

  /**
   * Parse a line into a collection of CSV fields.
   */
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf", "org.wartremover.warts.Null"))
  private[ceesvee] def parseLine[C[_]](
    bytes: Array[Byte],
    charset: Charset,
    options: Options,
  )(implicit f: Factory[String, C[String]]): C[String] = {

    val builder = f.newBuilder
    var builderEmpty = true
    var insideQuote = false
    var sliceStart = 0

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
      while (java.lang.Long.bitCount(quoteMaskBitsSet) > 0) {
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
      val numQuoteChars = java.lang.Long.bitCount(quoteChars)
      insideQuote = (numQuoteChars + (if (insideQuote) 1 else 0)) % 2 == 1

      val commaChars = vector.eq(Comma).toLong
      val commaIgnoringWithinQuotes = commaChars & ~(commaChars & quoteMask)

      /* | = \n
          a,"b""c","d,e","",f
          0010110101000101100 = quoteChars
          0100000010010010010 = commaChars
          0100000010000010010 = commaIgnoringWithinQuotes
       */

      var commaIgnoringWithinQuotesBitsSet = commaIgnoringWithinQuotes
      while (java.lang.Long.bitCount(commaIgnoringWithinQuotesBitsSet) > 0) {
        val r = java.lang.Long.numberOfTrailingZeros(commaIgnoringWithinQuotesBitsSet)
        commaIgnoringWithinQuotesBitsSet = commaIgnoringWithinQuotesBitsSet ^ java.lang.Long.lowestOneBit(commaIgnoringWithinQuotesBitsSet)

        val sliceTo = i + r
        val str = handleField(arraySlice(bytes, sliceStart, sliceTo, i, 0), charset, options)
        if (builderEmpty && ignoreTrimmedLine(str, options)) {
          i = bytes.length
          commaIgnoringWithinQuotesBitsSet = 0
        } else {
          val _ = builder += str
          builderEmpty = false
          sliceStart = sliceTo + 1
        }
      }

      i = i + ByteVectorSpecies.length
    }

    val remaining = if (sliceStart == 0) {
      bytes
    } else {
      bytes.slice(sliceStart, bytes.length)
    }

    val str = handleField(remaining, charset, options)
    if (builderEmpty && ignoreTrimmedLine(str, options)) {
      ()
    } else {
      val _ = builder += str
      builderEmpty = false
    }

    if (builderEmpty) null.asInstanceOf[C[String]] else builder.result()
  }

  private def handleField(bytes: Array[Byte], charset: Charset, options: Options) = {
    val str = new String(bytes, charset)
    // always ignore whitespace around a quoted cell
    val trimmed = Options.Trim.True.strip(str)

    val s = if (trimmed.length >= 2 && trimmed(0) == '"' && trimmed(trimmed.length - 1) == '"') {
      trimmed.substring(1, trimmed.length - 1)
    } else {
      options.trim.strip(str)
    }

    s.replace("\"\"", "\"")
  }

  private def arraySlice(src: Array[Byte], from: Int, to: Int, offset: Int, ignore: Long) = {
    var from_ = from
    var to_ = to
    var ignoreCount = 0

    var ignoreBitsSet = ignore
    while (java.lang.Long.bitCount(ignoreBitsSet) > 0) {
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
        System.arraycopy(src, from_, dest, 0, size)
      } else {
        var srcPosition = from_
        var destPosition = 0

        var ignoreBitsSet2 = ignore
        while (java.lang.Long.bitCount(ignoreBitsSet2) > 0) {
          val i = java.lang.Long.numberOfTrailingZeros(ignoreBitsSet2) + offset
          ignoreBitsSet2 = ignoreBitsSet2 ^ java.lang.Long.lowestOneBit(ignoreBitsSet2)

          if (i < from_ || i > to_) {
            ()
          } else if (srcPosition == i) {
            srcPosition = i + 1
          } else {
            val positionSize = srcPosition - i
            System.arraycopy(src, srcPosition, dest, destPosition, positionSize)
            srcPosition = i + 1
            destPosition = destPosition + positionSize
          }
        }
        if (srcPosition < to_) {
          System.arraycopy(src, srcPosition, dest, destPosition, to_ - srcPosition)
        }
      }

      dest
    }
  }
}
