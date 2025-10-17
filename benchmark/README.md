
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.1, OpenJDK 64-Bit Server VM, 25.0.1
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt   10  264.042 ± 0.890  us/op
ParserBenchmark.ceesveeVector  avgt   10  125.589 ± 0.328  us/op
ParserBenchmark.scalaCsv       avgt   10  761.886 ± 2.132  us/op
ParserBenchmark.univocity      avgt   10  194.973 ± 1.283  us/op
```

[info] ParserBenchmark.ceesvee        avgt   10  300.746 ± 1.621  us/op
[info] ParserBenchmark.ceesveeVector  avgt   10  130.967 ± 0.401  us/op
[info] ParserBenchmark.scalaCsv       avgt   10  739.959 ± 1.924  us/op
[info] ParserBenchmark.univocity      avgt   10  198.496 ± 0.393  us/op


`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.1, OpenJDK 64-Bit Server VM, 25.0.1
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.107 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ±  0.001  us/op
```
