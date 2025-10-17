
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.37
# VM version: JDK 25.0.1, OpenJDK 64-Bit Server VM, 25.0.1
Benchmark                  Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee    avgt   10  261.357 ± 1.787  us/op
ParserBenchmark.scalaCsv   avgt   10  741.778 ± 6.433  us/op
ParserBenchmark.univocity  avgt   10  200.482 ± 2.715  us/op
```

```
# JMH version: 1.37
#  VM version: JDK 25, OpenJDK 64-Bit Server VM, 25+37-jvmci-b01
Benchmark                  Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee    avgt   10  197.994 ± 2.344  us/op
ParserBenchmark.scalaCsv   avgt   10  776.080 ± 1.457  us/op
ParserBenchmark.univocity  avgt   10  208.226 ± 2.501  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 25.0.1, OpenJDK 64-Bit Server VM, 25.0.1
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.110 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ±  0.001  us/op
```
