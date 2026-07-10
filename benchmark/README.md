
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.3, OpenJDK 64-Bit Server VM, 25.0.3
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt   10  214.007 ± 2.136  us/op
ParserBenchmark.ceesveeVector  avgt   10   96.536 ± 0.203  us/op
ParserBenchmark.scalaCsv       avgt   10  750.618 ± 1.876  us/op
ParserBenchmark.univocity      avgt   10  196.028 ± 1.183  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.3, OpenJDK 64-Bit Server VM, 25.0.3+9-jvmci-25.1-b19
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt   10  117.435 ± 0.841  us/op
ParserBenchmark.ceesveeVector  avgt   10  969.052 ± 3.997  us/op
ParserBenchmark.scalaCsv       avgt   10  782.370 ± 2.014  us/op
ParserBenchmark.univocity      avgt   10  202.184 ± 1.849  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.3, OpenJDK 64-Bit Server VM, 25.0.3
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.107 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ±  0.001  us/op
```
