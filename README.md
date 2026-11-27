### ceeSvee

[![Maven Central](https://img.shields.io/maven-central/v/io.github.guymers/ceesvee-core_3)](https://central.sonatype.com/namespace/io.github.guymers)

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

Header names must be unique. When a column name occurs more than once in the input, the first occurrence is used.

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

#### Encoding

`CsvRecordEncoder` is the inverse of `CsvRecordDecoder`:

```scala
case class Test(
  str: String,
  int: Int,
  bool: Boolean,
  optInt: Option[Int],
) derives CsvRecordEncoder
```

The writer takes any `Iterable[String]` of header names and always uses `,` as the delimiter.

`Iterator`
```scala
val rows: Iterator[Test] = ???
val lines: Iterator[String] = CsvWriter.encodeWithHeader(Test.header, rows)
```

`fs2`
```scala
val rows: fs2.Stream[F, Test] = ???
val lines: fs2.Stream[F, String] = Fs2CsvWriter.encodeWithHeader(Test.header, rows)
```

`zio`
```scala
val rows: zio.stream.ZStream[R, E, Test] = ???
val lines: zio.stream.ZStream[R, E, String] = ZioCsvWriter.encodeWithHeader(Test.header, rows)
```

#### Vector API

There are also byte based vector parsers which can be faster than the string based parsers, but the result depends on the JVM: see the [benchmarks README](benchmark/README.md). The API is incubating, so the JVM must be started with the module enabled:

```
--add-modules=jdk.incubator.vector
```

`ceesvee.VectorAPIAvailable` can be used to check whether the module is present at runtime.
