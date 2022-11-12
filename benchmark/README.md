
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.32
# VM version: JDK 17.0.5, OpenJDK 64-Bit Server VM, 17.0.5+8-jvmci-22.3-b08
Benchmark                  Mode  Cnt     Score   Error  Units
ParserBenchmark.ceesvee    avgt   10   220.353 ± 3.128  us/op
ParserBenchmark.scalaCsv   avgt   10  1129.920 ± 8.480  us/op
ParserBenchmark.univocity  avgt   10   316.237 ± 3.499  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.32
# VM version: JDK 17.0.5, OpenJDK 64-Bit Server VM, 17.0.5+8-jvmci-22.3-b08
Benchmark                              Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee               avgt   10  1.426 ±  0.008  us/op
DecoderBenchmark.ceesveeRowNotIndexed  avgt   10  1.452 ±  0.015  us/op
DecoderBenchmark.univocity             avgt   10  0.012 ±  0.001  us/op
```
