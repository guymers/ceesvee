
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 24.0.1, OpenJDK 64-Bit Server VM, 24.0.1
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt   10  265.108 ± 0.380  us/op
ParserBenchmark.ceesveeVector  avgt   10  136.380 ± 1.534  us/op
ParserBenchmark.scalaCsv       avgt   10  731.281 ± 2.782  us/op
ParserBenchmark.univocity      avgt   10  199.028 ± 0.386  us/op
```

[info] # VM version: JDK 24.0.1, OpenJDK 64-Bit Server VM, 24.0.1+9-jvmci-b01
[info] Benchmark                      Mode  Cnt    Score   Error  Units
[info] ParserBenchmark.ceesvee        avgt   10  257.217 ± 2.313  us/op
[info] ParserBenchmark.ceesveeVector  avgt   10  687.391 ± 1.422  us/op
[info] ParserBenchmark.scalaCsv       avgt   10  758.006 ± 3.298  us/op
[info] ParserBenchmark.univocity      avgt   10  206.350 ± 0.392  us/op

AMD Ryzen 5 5600X
```
# JMH version: 1.37
# VM version: JDK 24.0.1, OpenJDK 64-Bit Server VM, 24.0.1
Benchmark                      Mode  Cnt    Score    Error  Units
ParserBenchmark.ceesvee        avgt   10  429.198 ±  0.751  us/op
ParserBenchmark.ceesveeVector  avgt   10  458.472 ±  0.625  us/op
ParserBenchmark.scalaCsv       avgt   10  742.206 ±  1.902  us/op
ParserBenchmark.univocity      avgt   10  298.406 ± 14.804  us/op
```


`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 23.0.2, OpenJDK 64-Bit Server VM, 23.0.2+7-jvmci-b01
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.115 ± 0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ± 0.001  us/op
```
