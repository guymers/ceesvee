package ceesvee

import java.nio.charset.StandardCharsets
import jdk.incubator.vector.ByteVector
import jdk.incubator.vector.VectorMask
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

  /**
   * @see
   *   [[CsvParser.parse]]
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def parse[C[_]](
    in: Iterator[Array[Byte]],
    options: Options,
  )(implicit f: Factory[String, C[String]]): Iterator[C[String]] = {
    splitLines(in, options)
      .filter(bytes => !CsvParser.ignoreLine(new String(bytes, Utf8), options))
      .map(parseLine(_, options))
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
          lines.foreach { line =>
            if (line.length > options.maximumLineLength) {
              throw Error.LineTooLong(options.maximumLineLength)
            }
          }
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
  private val Tab: Byte = '\t'
  private val NewLine: Byte = '\n'
  private val CarriageReturn: Byte = '\r'
  private val Utf8 = StandardCharsets.UTF_8

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

      val quotes = vector.eq(Quote)
      var mask = quotes
      var betweenQuotes = 0L

      // set all bits between quotes
      var quoteStart = if (insideQuote) 0 else -1
      while (mask.anyTrue()) {
        val r = mask.firstTrue()
        mask = mask.xor(VectorMask.fromLong(vector.species(), 1L << r))

        if (quoteStart >= 0) {
          var j = r - 1
          while (j >= quoteStart) {
            betweenQuotes = betweenQuotes | (1L << j)
            j = j - 1
          }
          quoteStart = -1
        } else {
          quoteStart = r
        }
      }
      if (quoteStart >= 0) {
        var j = ByteVectorSpecies.length - 1
        while (j >= quoteStart) {
          betweenQuotes = betweenQuotes | (1L << j)
          j = j - 1
        }
      }

      val quoteMask = quotes.or(VectorMask.fromLong(vector.species(), betweenQuotes))
      insideQuote = (quotes.trueCount() + (if (insideQuote) 1 else 0)) % 2 == 1

      val crChars = vector.eq(CarriageReturn)
      val crIgnoringWithinQuotes = crChars.andNot(crChars.and(quoteMask))
      val nlChars = vector.eq(NewLine)
      val nlIgnoringWithinQuotes = nlChars.andNot(nlChars.and(quoteMask))

      /* \ = \r, | = \n
          a\|b\c|"d\|e"|f
          000000010000100 = quoteChars
          000000011111100 = quoteMask
          010010000100000 = crChars
          010010000000000 = crIgnoringWithinQuotes
          001000100010010 = nlChars
          001000100000010 = nlIgnoringWithinQuotes
       */

      var nlIgnoringWithinQuotesBitSet = nlIgnoringWithinQuotes.toLong
      while (java.lang.Long.bitCount(nlIgnoringWithinQuotesBitSet) > 0) {
        val r = java.lang.Long.numberOfTrailingZeros(nlIgnoringWithinQuotesBitSet)
        nlIgnoringWithinQuotesBitSet = nlIgnoringWithinQuotesBitSet ^ java.lang.Long.lowestOneBit(nlIgnoringWithinQuotesBitSet)

        val isPrevCr =
          (r == 0 && prevCarriageReturn) ||
          (r > 0 && crIgnoringWithinQuotes.laneIsSet(r - 1))

        val sliceTo = i + r
        val crFromPreviousChunk = i == 0 && r == 0 && isPrevCr
        val leftover = if (sliceStart == 0) {
          if (crFromPreviousChunk && state.leftover.nonEmpty) {
            state.leftover.slice(0, state.leftover.length - 1)
          } else state.leftover
        } else {
          Array.emptyByteArray
        }
        val to = if (isPrevCr && !crFromPreviousChunk) sliceTo - 1 else sliceTo
        val _ = builder += leftover ++ arraySlice(bytes, sliceStart, to, i, 0)

        sliceStart = sliceTo + 1
      }

      val vectorLength = Math.min(ByteVectorSpecies.length, bytes.length - i)
      prevCarriageReturn = crIgnoringWithinQuotes.laneIsSet(vectorLength - 1)
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
  private[ceesvee] def parseLine[C[_]](
    bytes: Array[Byte],
    options: Options,
  )(implicit f: Factory[String, C[String]]): C[String] = {

    val builder = f.newBuilder
    var insideQuote = false
    var sliceStart = 0
    val delimiter = options.delimiter match {
      case Options.Delimiter.Comma => Comma
      case Options.Delimiter.Tab => Tab
    }

    val loopBound = ByteVectorSpecies.loopBound(bytes.length)

    var i = 0
    while (i < bytes.length) {
      val vector = if (i >= loopBound) {
        val m = ByteVectorSpecies.indexInRange(i, bytes.length)
        ByteVector.fromArray(ByteVectorSpecies, bytes, i, m)
      } else {
        ByteVector.fromArray(ByteVectorSpecies, bytes, i)
      }

      val quotes = vector.eq(Quote)
      var mask = quotes
      var betweenQuotes = 0L

      // set all bits between quotes
      var quoteStart = if (insideQuote) 0 else -1
      while (mask.anyTrue()) {
        val r = mask.firstTrue()
        mask = mask.xor(VectorMask.fromLong(vector.species(), 1L << r))

        if (quoteStart >= 0) {
          var j = r - 1
          while (j >= quoteStart) {
            betweenQuotes = betweenQuotes | (1L << j)
            j = j - 1
          }
          quoteStart = -1
        } else {
          quoteStart = r
        }
      }
      if (quoteStart >= 0) {
        var j = ByteVectorSpecies.length - 1
        while (j >= quoteStart) {
          betweenQuotes = betweenQuotes | (1L << j)
          j = j - 1
        }
      }

      val quoteMask = quotes.or(VectorMask.fromLong(vector.species(), betweenQuotes))
      insideQuote = (quotes.trueCount() + (if (insideQuote) 1 else 0)) % 2 == 1

      val delimiterChars = vector.eq(delimiter)
      val delimiterIgnoringWithinQuotes = delimiterChars.andNot(delimiterChars.and(quoteMask))

      /*
          a,"b""c","d,e","",f
          0010110101000101100 = quoteChars
          0100000010010010010 = commaChars
          0100000010000010010 = commaIgnoringWithinQuotes
       */

      var delimiterIgnoringWithinQuotesBitSet = delimiterIgnoringWithinQuotes.toLong
      while (java.lang.Long.bitCount(delimiterIgnoringWithinQuotesBitSet) > 0) {
        val r = java.lang.Long.numberOfTrailingZeros(delimiterIgnoringWithinQuotesBitSet)
        delimiterIgnoringWithinQuotesBitSet =
          delimiterIgnoringWithinQuotesBitSet ^ java.lang.Long.lowestOneBit(delimiterIgnoringWithinQuotesBitSet)

        val sliceTo = i + r
        val str = handleField(arraySlice(bytes, sliceStart, sliceTo, i, 0), options)
        val _ = builder += str
        sliceStart = sliceTo + 1
      }

      i = i + ByteVectorSpecies.length
    }

    val remaining = if (sliceStart == 0) {
      bytes
    } else {
      bytes.slice(sliceStart, bytes.length)
    }

    val str = handleField(remaining, options)
    val _ = builder += str

    builder.result()
  }

  private def handleField(bytes: Array[Byte], options: Options) = {
    val str = new String(bytes, Utf8)
    val trimmed = Options.Trim.True.strip(str)

    if (trimmed.length >= 2 && trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length - 1) == '"') {
      trimmed.substring(1, trimmed.length - 1).replace("\"\"", "\"")
    } else {
      options.trim.strip(str)
    }
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
