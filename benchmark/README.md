
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.37
# VM version: JDK 21, OpenJDK 64-Bit Server VM, 21+35-jvmci-23.1-b15
Benchmark                  Mode  Cnt     Score   Error  Units
ParserBenchmark.ceesvee    avgt   10   297.772 ± 1.999  us/op
ParserBenchmark.scalaCsv   avgt   10  1103.847 ± 3.684  us/op
ParserBenchmark.univocity  avgt   10   311.349 ± 1.811  us/op
```

`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.DecoderBenchmark`

```
# JMH version: 1.37
# VM version: JDK 21, OpenJDK 64-Bit Server VM, 21+35-jvmci-23.1-b15
Benchmark                   Mode  Cnt  Score    Error  Units
DecoderBenchmark.ceesvee    avgt   10  0.130 ±  0.001  us/op
DecoderBenchmark.univocity  avgt   10  0.012 ±  0.001  us/op
```
