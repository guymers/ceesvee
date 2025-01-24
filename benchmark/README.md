
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.37
# VM version: JDK 23.0.2, OpenJDK 64-Bit Server VM, 23.0.2+7-jvmci-b01
Benchmark                  Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee    avgt   10  267.157 ± 2.295  us/op
ParserBenchmark.scalaCsv   avgt   10  776.875 ± 3.156  us/op
ParserBenchmark.univocity  avgt   10  190.484 ± 0.927  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 23.0.2, OpenJDK 64-Bit Server VM, 23.0.2+7-jvmci-b01
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.115 ± 0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.011 ± 0.001  us/op
```
