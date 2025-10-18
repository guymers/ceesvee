
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.1, OpenJDK 64-Bit Server VM, 25.0.1
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt   10  263.230 ± 0.679  us/op
ParserBenchmark.ceesveeVector  avgt   10  134.205 ± 0.302  us/op
ParserBenchmark.scalaCsv       avgt   10  748.232 ± 2.016  us/op
ParserBenchmark.univocity      avgt   10  198.765 ± 0.982  us/op
```

```
# JMH version: 1.37
#  VM version: JDK 25, OpenJDK 64-Bit Server VM, 25+37-jvmci-b01
Benchmark                      Mode  Cnt     Score    Error  Units
ParserBenchmark.ceesvee        avgt   10   187.441 ±  1.345  us/op
ParserBenchmark.ceesveeVector  avgt   10  1484.755 ± 14.298  us/op
ParserBenchmark.scalaCsv       avgt   10   780.945 ±  2.340  us/op
ParserBenchmark.univocity      avgt   10   204.178 ±  1.702  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.1, OpenJDK 64-Bit Server VM, 25.0.1
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.110 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ±  0.001  us/op
```
