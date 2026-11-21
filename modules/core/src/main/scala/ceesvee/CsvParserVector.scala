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
  )(using f: Factory[String, C[String]]): Iterator[C[String]] = {
    val lines = splitLines(in, options)
    val withoutIgnoredLines = if (CsvParser.canIgnoreLines(options)) {
      lines.filter(bytes => !CsvParser.ignoreLine(new String(bytes, Utf8), options))
    } else {
      lines
    }
    withoutIgnoredLines.map(parseLine(_, options))
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
    // empty chunk has no line and would otherwise be returned as one at EOF
    private val chunks = in.filter(_.nonEmpty)
    private val toOutput = mutable.Queue.empty[Array[Byte]]
    private var state = State.initial

    override def hasNext = toOutput.nonEmpty || chunks.hasNext || state.leftover.nonEmpty

    @tailrec override def next() = {
      if (toOutput.nonEmpty) {
        toOutput.dequeue()
      } else {
        val leftover = state.leftover
        if (leftover.length > options.maximumLineLength) {
          throw Error.LineTooLong(options.maximumLineLength)
        }

        if (!chunks.hasNext) {
          if (leftover.nonEmpty) {
            state = State.initial
            leftover
          } else {
            throw new NoSuchElementException("next on empty iterator")
          }
        } else {
          val bytes = chunks.next()
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
  )(using f: Factory[Array[Byte], C[Array[Byte]]]): (State, C[Array[Byte]]) = {

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
      val quoteMask = VectorMask.fromLong(vector.species(), setAllBitsBetweenQuotes(quotes.toLong, insideQuote))
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

        val isPrevCr = (r == 0 && prevCarriageReturn) || (r > 0 && crIgnoringWithinQuotes.laneIsSet(r - 1))

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
        val slice = arraySlice(bytes, sliceStart, to)
        val _ = builder += (if (leftover.isEmpty) slice else leftover ++ slice)

        sliceStart = sliceTo + 1
      }

      val vectorLength = Math.min(ByteVectorSpecies.length, bytes.length - i)
      prevCarriageReturn = crIgnoringWithinQuotes.laneIsSet(vectorLength - 1)
      i = i + ByteVectorSpecies.length
    }

    val leftover = if (sliceStart == 0) {
      if (state.leftover.isEmpty) bytes.clone() else state.leftover ++ bytes
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
  )(using f: Factory[String, C[String]]): C[String] = {

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
      val quoteMask = VectorMask.fromLong(vector.species(), setAllBitsBetweenQuotes(quotes.toLong, insideQuote))
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
        val str = handleField(bytes, sliceStart, sliceTo, options)
        val _ = builder += str
        sliceStart = sliceTo + 1
      }

      i = i + ByteVectorSpecies.length
    }

    val str = handleField(bytes, sliceStart, bytes.length, options)
    val _ = builder += str

    builder.result()
  }

  private def handleField(bytes: Array[Byte], from: Int, until: Int, options: Options) = {
    CsvParser.trimStringAndUnescape(options, new String(bytes, from, until - from, Utf8))
  }

  private def setAllBitsBetweenQuotes(quotes: Long, startsInsideQuote: Boolean) = {
    var parity = quotes
    parity = parity ^ (parity << 1)
    parity = parity ^ (parity << 2)
    parity = parity ^ (parity << 4)
    parity = parity ^ (parity << 8)
    parity = parity ^ (parity << 16)
    parity = parity ^ (parity << 32)
    (if (startsInsideQuote) ~parity else parity) | quotes
  }

  private def arraySlice(src: Array[Byte], from: Int, to: Int) = {
    val size = to - from

    if (size <= 0) {
      Array.emptyByteArray
    } else {
      val dest = Array.ofDim[Byte](size)
      System.arraycopy(src, from, dest, 0, size)
      dest
    }
  }
}
