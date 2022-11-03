
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
# JMH version: 1.32
# VM version: JDK 17.0.5, OpenJDK 64-Bit Server VM, 17.0.5+8-jvmci-22.3-b08
Benchmark                  Mode  Cnt     Score   Error  Units
ParserBenchmark.ceesvee    avgt   10   231.184 ± 2.460  us/op
ParserBenchmark.scalaCsv   avgt   10  1085.851 ± 5.973  us/op
ParserBenchmark.univocity  avgt   10   312.406 ± 1.546  us/op
```
