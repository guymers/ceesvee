
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.5.1, OpenJDK 64-Bit Server VM, 25.0.5.1
ParserBenchmark.ceesvee        avgt   10  146.184 ± 0.276  us/op
ParserBenchmark.ceesveeVector  avgt   10   99.182 ± 0.335  us/op
ParserBenchmark.scalaCsv       avgt   10  792.948 ± 1.385  us/op
ParserBenchmark.univocity      avgt   10  199.626 ± 0.878  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.4.1.1, OpenJDK 64-Bit Server VM, 25.0.4.1.1+1-jvmci-25.4-b23
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt   10  119.230 ± 0.505  us/op
ParserBenchmark.ceesveeVector  avgt   10  576.901 ± 1.299  us/op
ParserBenchmark.scalaCsv       avgt   10  736.395 ± 1.178  us/op
ParserBenchmark.univocity      avgt   10  200.094 ± 1.090  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.5.1, OpenJDK 64-Bit Server VM, 25.0.5.1
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.099 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.006 ±  0.001  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.4.1.1, OpenJDK 64-Bit Server VM, 25.0.4.1.1+1-jvmci-25.4-b23
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.053 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.006 ±  0.001  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.WriterBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.5.1, OpenJDK 64-Bit Server VM, 25.0.5.1
Benchmark                                          Mode  Cnt    Score   Error  Units
WriterBenchmark.ceesveeEncodeWithHeader            avgt   10  552.325 ± 2.105  us/op
WriterBenchmark.ceesveeFieldsToLine                avgt   10  319.633 ± 1.557  us/op
WriterBenchmark.ceesveeFieldsToLineWithoutQuoting  avgt   10  251.664 ± 2.039  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.4.1.1, OpenJDK 64-Bit Server VM, 25.0.4.1.1+1-jvmci-25.4-b23
Benchmark                                          Mode  Cnt    Score   Error  Units
WriterBenchmark.ceesveeEncodeWithHeader            avgt   10  509.721 ± 3.291  us/op
WriterBenchmark.ceesveeFieldsToLine                avgt   10  277.629 ± 0.574  us/op
WriterBenchmark.ceesveeFieldsToLineWithoutQuoting  avgt   10  195.952 ± 0.155  us/op
```
