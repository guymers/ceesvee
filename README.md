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
  val csvHeader = CsvHeader.create(header)(decoder)
}
```

`Iterator`
```scala
val input: Iterator[String]
val result: Either[CsvHeader.MissingHeaders, Iterator[Either[CsvRecordDecoder.Error, Test]]] =
  CsvParser.decodeWithHeader(input, Test.csvHeader, options)
```

`fs2`
```scala
val stream: fs2.Stream[F[?], String]
val result: fs2.Stream[F[?], Either[CsvRecordDecoder.Error, Test]] = stream.through {
  Fs2CsvParser.decodeWithHeader(Test.csvHeader, options)
}
```

`zio`
```scala
val stream: zio.stream.ZStream[R, E, String]
val result: zio.ZIO[
  Scope & R,
  Either[Either[E, CsvParser.Error], CsvHeader.MissingHeaders],
  zio.stream.ZStream[E, Either[E, CsvParser.Error], Either[CsvRecordDecoder.Error, Test]]
] = ZioCsvParser.decodeWithHeader(stream, Test.csvHeader, options)
```
