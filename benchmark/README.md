
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

AMD Ryzen 9 9950X
```
# JMH version: 1.37
# VM version: JDK 25.0.3, OpenJDK 64-Bit Server VM, 25.0.3
ParserBenchmark.ceesvee        avgt   10  272.163 ± 1.733  us/op
ParserBenchmark.ceesveeVector  avgt   10  131.243 ± 0.577  us/op
ParserBenchmark.scalaCsv       avgt   10  740.304 ± 3.177  us/op
ParserBenchmark.univocity      avgt   10  199.074 ± 0.384  us/op
```

```
# JMH version: 1.37
# VM version: JDK 25.0.3, OpenJDK 64-Bit Server VM, 25.0.3+9-jvmci-25.1-b19
ParserBenchmark.ceesvee        avgt   10   194.363 ±  1.911  us/op
ParserBenchmark.ceesveeVector  avgt   10  1454.794 ± 11.985  us/op
ParserBenchmark.scalaCsv       avgt   10   791.310 ±  1.224  us/op
ParserBenchmark.univocity      avgt   10   207.725 ±  0.946  us/op
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
