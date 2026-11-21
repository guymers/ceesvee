### ceeSvee

A Scala 3 CSV library designed for use with streams. Can be used with `Iterator`, `fs2.Stream` and `zio.stream.ZStream`.

Supports the CSV format described by [Model for Tabular Data and Metadata on the Web](https://www.w3.org/TR/2015/REC-tabular-data-model-20151217/#ebnf):
- a new line can be `\n` or `\r\n`
- a double quote is escaped by a double quote

The `core` module has no required dependencies, `fs2` depends on `fs2-core` and `zio` depends on `zio` and `zio-streams`.

#### Example

```scala
case class Test(
  str: String,
  int: Int,
  bool: Boolean,
  optInt: Option[Int],
) derives CsvRecordDecoder
object Test {
  val header = ::("str", List("int", "bool", "opt_int"))
  val csvHeader: CsvHeader[Test] = CsvHeader.create(header)
}
```

The header names can also be given as a non-empty tuple of `String`s:

```scala
val csvHeader: CsvHeader[Test] = CsvHeader.createFromTuple(("str", "int", "bool", "opt_int"))
```

Header names must be unique. When a column name occurs more than once in the
input, the first occurrence is used.

#### Decoding

`Iterator`
```scala
val input: Iterator[String]
val result: Either[CsvHeader.MissingHeaders, Iterator[Either[CsvHeader.Errors, Test]]] =
  CsvReader.decodeWithHeader(input, Test.csvHeader, options)
```

`fs2`
```scala
val stream: fs2.Stream[F, String]
val result: fs2.Stream[F, Either[CsvHeader.Errors, Test]] = stream.through {
  Fs2CsvReader.decodeWithHeader[F, Test](Test.csvHeader, options)
}
```

`zio`
```scala
val stream: zio.stream.ZStream[R, E, String]
val result: zio.stream.ZStream[R, Either[E, ZioCsvReader.Error], Either[CsvHeader.Errors, Test]] =
  ZioCsvReader.decodeWithHeader(stream, Test.csvHeader, options)
```

An empty input raises `CsvHeader.MissingHeaders` (`CsvReader.decodeWithHeader`
returns it as a `Left`). The same happens when the first row does not contain
every expected header.

#### Encoding

`CsvRecordEncoder` is the inverse of `CsvRecordDecoder`:

```scala
case class Test(
  str: String,
  int: Int,
) derives CsvRecordEncoder
```

`Iterator`
```scala
val lines: Iterator[String] = CsvWriter.encodeWithHeader(Test.header, rows, writerOptions)
```

`fs2` / `zio`
```scala
val fs2Lines: fs2.Stream[F, String] = Fs2CsvWriter.encodeWithHeader(Test.header, rows, writerOptions)
val zioLines: zio.stream.ZStream[R, E, String] = ZioCsvWriter.encodeWithHeader(Test.header, rows, writerOptions)
```

A record that would otherwise encode to an empty line (no fields, or a single
empty field) is written as a quoted empty field (`""`) so that it is not
mistaken for a blank line or the end of the input when read back.

#### Options

`CsvParser.Options` / `CsvReader.Options` control both parsing and decoding:

| option | default | description |
| --- | --- | --- |
| `commentPrefix` | `None` | ignore lines that start with this prefix (after trimming) |
| `maximumLineLength` | `10000` | longest line, in UTF-16 code units for string parsers and UTF-8 bytes for byte parsers |
| `skipBlankRows` | `false` | ignore blank lines |
| `trim` | `Trim.True` | trim whitespace around fields (`True`, `False`, `Start`, `End`) |
| `delimiter` | `Delimiter.Comma` | field delimiter (`Comma` or `Tab`) |

The writer has its own smaller set of options:

```scala
val writerOptions = CsvWriter.Options(
  delimiter = CsvParser.Options.Delimiter.Tab,
  commentPrefix = Some("#"),
)
// or derive them from the options used for reading
val sameOptions = CsvWriter.Options.from(CsvReader.Options.Defaults)
```

The writer quotes a first field that starts with the `commentPrefix` so that
the row is not dropped as a comment when read back.

#### Errors

`CsvRecordDecoder.Errors.errors` is keyed by field index. An error reported by
`CsvRecordDecoder.emap` applies to the whole record and is stored at
`CsvRecordDecoder.Errors.RecordIndex` (`-1`) rather than against a field.

`CsvHeader.Errors.errors` is keyed by header name. Record level errors are
stored at `CsvHeader.Errors.RecordKey`.

#### BOMs

A UTF-8 BOM is not stripped. Remove it before decoding, for example by dropping
the first three bytes of a byte stream or a leading `\uFEFF` character from a
string stream:

```scala
val withoutBom = bytes.drop(3)
val line = if (str.startsWith("\uFEFF")) str.drop(1) else str
```

#### Chunking contract

The string based parsers split and concatenate the input chunks on `\n` and
`\r\n`. Each chunk must therefore contain the new lines that terminate its
records: a chunk without a new line is an incomplete line that is joined with
the next chunk. Do not feed line oriented sources directly:

- `fs2.text.lines`, `Files.lines`, `Source.getLines` and similar strip the new
  lines, so every line would be concatenated into one record and eventually
  fail with `CsvParser.Error.LineTooLong`.
- Use a source that preserves the new lines, such as `Files.readString`,
  `fs2.io.file.Files.readUtf8` or `fs2.text.utf8.decode`, instead.

The byte based vector parsers do not have this restriction: byte chunks may be
split at any point, including inside a multi-byte UTF-8 character.

#### Vector API

`CsvParserVector`, `Fs2CsvParserVector`, `ZioCsvParserVector` and
`ZioCsvReaderVector` use the Java vector API (`jdk.incubator.vector`) and are
substantially faster for large inputs. The API is incubating, so the JVM must
be started with the module enabled:

```
--add-modules=jdk.incubator.vector
```

`ceesvee.VectorAPIAvailable` can be used to check whether the module is present
at runtime.
