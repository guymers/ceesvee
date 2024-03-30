
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.37
# VM version: JDK 21.0.2, OpenJDK 64-Bit Server VM, 21.0.2+13-jvmci-23.1-b30
Benchmark                  Mode  Cnt     Score    Error  Units
ParserBenchmark.ceesvee    avgt   10   322.808 ±  3.382  us/op
ParserBenchmark.scalaCsv   avgt   10  1066.774 ± 10.905  us/op
ParserBenchmark.univocity  avgt   10   309.066 ±  7.617  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 21.0.2, OpenJDK 64-Bit Server VM, 21.0.2+13-jvmci-23.1-b30
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.136 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.012 ±  0.001  us/op
```
