
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.32
# VM version: JDK 17.0.6, OpenJDK 64-Bit Server VM, 17.0.6+10-jvmci-22.3-b13
Benchmark                  Mode  Cnt     Score   Error  Units
ParserBenchmark.ceesvee    avgt   10   326.390 ± 3.517  us/op
ParserBenchmark.scalaCsv   avgt   10  1145.596 ± 7.898  us/op
ParserBenchmark.univocity  avgt   10   322.381 ± 3.118  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.32
# VM version: JDK 17.0.6, OpenJDK 64-Bit Server VM, 17.0.6+10-jvmci-22.3-b13
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.122 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.012 ±  0.001  us/op
```
