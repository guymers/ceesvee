
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 24.0.1, OpenJDK 64-Bit Server VM, 24.0.1
Benchmark                      Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee        avgt    5  279.296 ± 1.040  us/op
ParserBenchmark.ceesveeVector  avgt    5  145.234 ± 1.457  us/op
ParserBenchmark.scalaCsv       avgt    5  712.414 ± 5.120  us/op
ParserBenchmark.univocity      avgt    5  193.677 ± 2.019  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 23.0.2, OpenJDK 64-Bit Server VM, 23.0.2+7-jvmci-b01
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.115 ± 0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ± 0.001  us/op
```
