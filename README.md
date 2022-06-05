### ceeSvee

A CSV parser designed for use with steams. Can be used with `Iterator`, `fs2.Stream` and `zio.stream.ZStream`.

Supports the CSV format described by [Model for Tabular Data and Metadata on the Web](https://www.w3.org/TR/2015/REC-tabular-data-model-20151217/#ebnf):
- a new line can be `\n` or `\r\n`
- a double quote is escaped by a double quote 
- leading and trailing whitespace in a field is ignored

#### Example

```scala
case class Test(
  str: String,
  int: Int,
  bool: Boolean,
  optInt: Option[Int],
)
object Test {
  implicit val decoder: CsvRecordDecoder[Test] = CsvRecordDecoder.derive
}
```

`Iterator`
```scala
val input: Iterator[String]
val result: Iterator[Either[CsvRecordDecoder.Error, Test]] = 
  CsvParser.decode[Test](input, options)
```
