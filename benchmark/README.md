
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.37
# VM version: JDK 25.0.5.1, OpenJDK 64-Bit Server VM, 25.0.5.1
Benchmark                      Mode  Cnt    Score    Error  Units
ParserBenchmark.ceesvee        avgt   10  149.352 ±  0.343  us/op
ParserBenchmark.ceesveeVector  avgt   10   89.909 ±  0.164  us/op
ParserBenchmark.scalaCsv       avgt   10  815.975 ±  3.131  us/op
ParserBenchmark.univocity      avgt   10  200.769 ±  0.288  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.4.1.1, OpenJDK 64-Bit Server VM, 25.0.4.1.1+1-jvmci-25.4-b23
Benchmark                      Mode  Cnt    Score    Error  Units
ParserBenchmark.ceesvee        avgt   10  120.293 ±  0.470  us/op
ParserBenchmark.ceesveeVector  avgt   10  548.682 ±  1.075  us/op
ParserBenchmark.scalaCsv       avgt   10  736.035 ±  0.680  us/op
ParserBenchmark.univocity      avgt   10  200.027 ±  0.282  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 25.0.5.1, OpenJDK 64-Bit Server VM, 25.0.5.1
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.070 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.006 ±  0.001  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.4.1.1, OpenJDK 64-Bit Server VM, 25.0.4.1.1+1-jvmci-25.4-b23
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.038 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.006 ±  0.001  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.WriterBenchmark`

```
# JMH version: 1.37
# VM version: JDK 25.0.5.1, OpenJDK 64-Bit Server VM, 25.0.5.1
Benchmark                                          Mode  Cnt    Score    Error  Units
WriterBenchmark.ceesveeEncodeWithHeader            avgt   10  243.156 ±  1.170  us/op
WriterBenchmark.ceesveeFieldsToLine                avgt   10   86.234 ±  0.099  us/op
WriterBenchmark.ceesveeFieldsToLineWithoutQuoting  avgt   10   45.927 ±  0.040  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.4.1.1, OpenJDK 64-Bit Server VM, 25.0.4.1.1+1-jvmci-25.4-b23
Benchmark                                          Mode  Cnt    Score    Error  Units
WriterBenchmark.ceesveeEncodeWithHeader            avgt   10  195.640 ±  0.688  us/op
WriterBenchmark.ceesveeFieldsToLine                avgt   10   77.555 ±  0.075  us/op
WriterBenchmark.ceesveeFieldsToLineWithoutQuoting  avgt   10   56.545 ±  0.040  us/op
```
