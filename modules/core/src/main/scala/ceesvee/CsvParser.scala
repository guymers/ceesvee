package ceesvee

import scala.annotation.switch
import scala.annotation.tailrec
import scala.collection.Factory
import scala.collection.mutable

object CsvParser {

  trait Options {
    def commentPrefix: Option[String]
    def maximumLineLength: Int
    def skipBlankRows: Boolean
    def trim: Options.Trim
    def delimiter: Options.Delimiter
  }
  object Options {

    val Defaults: Impl = apply(
      commentPrefix = None,
      maximumLineLength = 10_000,
      skipBlankRows = false,
      trim = Trim.True,
      delimiter = Options.Delimiter.Comma,
    )

    case class Impl(
      commentPrefix: Option[String],
      maximumLineLength: Int,
      skipBlankRows: Boolean,
      trim: Trim,
      delimiter: Options.Delimiter,
    ) extends Options

    def apply(
      commentPrefix: Option[String],
      maximumLineLength: Int,
      skipBlankRows: Boolean,
      trim: Trim,
      delimiter: Options.Delimiter,
    ): Impl = Impl(
      commentPrefix = commentPrefix,
      maximumLineLength = maximumLineLength,
      skipBlankRows = skipBlankRows,
      trim = trim,
      delimiter = delimiter,
    )

    sealed abstract class Delimiter extends Serializable with Product
    object Delimiter {
      case object Comma extends Delimiter
      case object Tab extends Delimiter
    }

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
    val lines = splitLines(in, options)
    val withoutIgnoredLines = if (canIgnoreLines(options)) {
      lines.filter(str => !ignoreLine(str, options))
    } else {
      lines
    }
    withoutIgnoredLines.map(parseLine(_, options))
  }

  def ignoreLine(line: String, options: Options): Boolean = {
    val l = options.trim.strip(line)
    ignoreTrimmedLine(l, options)
  }

  private[ceesvee] def canIgnoreLines(options: Options): Boolean =
    options.skipBlankRows || options.commentPrefix.exists(_.nonEmpty)

  private[ceesvee] def ignoreTrimmedLine(line: String, options: Options): Boolean = {
    isBlank(line, options) || isComment(line, options)
  }

  private def isBlank(line: String, options: Options): Boolean = {
    options.skipBlankRows && line.isEmpty
  }

  private def isComment(line: String, options: Options): Boolean = {
    options.commentPrefix.filter(_.nonEmpty).exists(line.startsWith(_))
  }

  /**
   * Splits the given strings into CSV lines by splitting on either '\r\n' or
   * '\n'.
   *
   * '"' is the only valid escape for nested double quotes.
   */
  @throws[Error.LineTooLong]("if a line is longer than `maximumLineLength`")
  def splitLines(in: Iterator[String], options: Options): Iterator[String] = new SplitLinesIterator(in, options)
  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Throw",
    "org.wartremover.warts.Var",
  ))
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

  sealed trait Error
  object Error {
    final case class LineTooLong(maximum: Int) extends RuntimeException(
        s"CSV line exceeded maximum length of ${maximum.toString}",
      ) with Error
  }

  case class State(
    leftover: String,
    insideQuoteIndex: Int,
    previousCarriageReturn: Boolean,
  )
  object State {
    val initial: State = State(
      leftover = "",
      insideQuoteIndex = -1,
      previousCarriageReturn = false,
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
    var insideQuoteIndex = state.insideQuoteIndex
    var previousCarriageReturn = state.previousCarriageReturn
    var leftover = state.leftover

    val it = strings.iterator
    while (it.hasNext) {
      val string = it.next()
      if (string.nonEmpty) {

        val concat = leftover.concat(string)

        var insideQuote = false
        var i =
          if (insideQuoteIndex >= 0) insideQuoteIndex
          else if (previousCarriageReturn) leftover.length - 1
          else leftover.length
        var sliceStart = 0

        while (i < concat.length) {
          (concat(i): @switch) match {

            case '"' =>
              if (insideQuote) {
                if ((i + 1) == concat.length) { // last char
                  i += 1 // not enough information
                } else {
                  if (concat(i + 1) == '"') { // escaped quote
                    i += 2
                  } else {
                    insideQuote = false
                    insideQuoteIndex = -1
                    i += 1
                  }
                }
              } else {
                insideQuote = true
                insideQuoteIndex = i
                i += 1
              }

            case '\n' =>
              if (insideQuote) {
                i += 1
              } else {
                val _ = builder += concat.substring(sliceStart, i)
                i += 1
                sliceStart = i
              }

            case '\r' =>
              if (insideQuote) {
                i += 1
              } else {
                if ((i + 1) == concat.length) { // last char
                  i += 1 // previousCarriageReturn set later
                } else {
                  if (concat(i + 1) == '\n') {
                    val _ = builder += concat.substring(sliceStart, i)
                    i += 2
                    sliceStart = i
                  } else {
                    i += 1
                  }
                }
              }

            case _ =>
              i += 1
          }
        }

        insideQuoteIndex = insideQuoteIndex - sliceStart
        previousCarriageReturn = concat(i - 1) == '\r'
        leftover = concat.substring(sliceStart, concat.length)
      }
    }

    (State(leftover, insideQuoteIndex = insideQuoteIndex, previousCarriageReturn = previousCarriageReturn), builder.result())
  }

  /**
   * Parse a line into a collection of CSV fields.
   */
  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.NonUnitStatements",
    "org.wartremover.warts.Var",
    "org.wartremover.warts.While",
  ))
  def parseLine[C[_]](
    line: String,
    options: Options,
  )(implicit f: Factory[String, C[String]]): C[String] = {
    val fields = f.newBuilder
    val escapedField = new mutable.StringBuilder
    val delimiter = options.delimiter match {
      case Options.Delimiter.Comma => ','
      case Options.Delimiter.Tab => '\t'
    }
    var sliceStart = 0
    var i = 0
    var insideQuote = false

    while (i < line.length) {
      val char = line.charAt(i)
      if (char == delimiter && !insideQuote) {
        val field = if (escapedField.isEmpty) {
          line.substring(sliceStart, i)
        } else {
          escapedField.append(line.substring(sliceStart, i))
          val result = escapedField.result()
          escapedField.clear()
          result
        }
        fields.addOne(trimString(options, field))
        i += 1
        sliceStart = i
      } else if (char == '"') {
        if (insideQuote && (i + 1) < line.length && line.charAt(i + 1) == '"') {
          escapedField.append(line.substring(sliceStart, i))
          sliceStart = i + 1
          i += 2
        } else {
          i += 1
          insideQuote = !insideQuote
        }
      } else {
        i += 1
      }
    }

    val field = if (escapedField.isEmpty) {
      line.substring(sliceStart, i)
    } else {
      escapedField.append(line.substring(sliceStart, i))
      escapedField.result()
    }
    fields.addOne(trimString(options, field))

    fields.result()
  }

  private[ceesvee] def trimString(options: Options, str: String) = {
    // always ignore whitespace around a quoted cell
    val trimmed = Options.Trim.True.strip(str)

    if (trimmed.length >= 2 && trimmed.charAt(0) == '"' && trimmed.charAt(trimmed.length - 1) == '"') {
      trimmed.substring(1, trimmed.length - 1)
    } else {
      options.trim.strip(str)
    }
  }
}
