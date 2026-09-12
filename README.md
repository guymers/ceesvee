### ceeSvee

A CSV library designed for use with steams. Can be used with `Iterator`, `fs2.Stream` and `zio.stream.ZStream`.

Supports the CSV format described by [Model for Tabular Data and Metadata on the Web](https://www.w3.org/TR/2015/REC-tabular-data-model-20151217/#ebnf):
- a new line can be `\n` or `\r\n`
- a double quote is escaped by a double quote 

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

On Scala 3 the header names can be given as a non-empty tuple of `String`s:

```scala
val csvHeader: CsvHeader[Test] = CsvHeader.createFromTuple(("str", "int", "bool", "opt_int"))
```

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
