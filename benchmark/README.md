
`benchmark/Jmh/run -i 10 -wi 5 -f 1 -t 2 ceesvee.benchmark.ParserBenchmark`

```
Benchmark                  Mode  Cnt    Score   Error  Units
ParserBenchmark.ceesvee    avgt   10  358.229 ± 0.989  us/op
ParserBenchmark.scalaCsv   avgt   10  957.180 ± 7.205  us/op
ParserBenchmark.univocity  avgt   10  356.728 ± 3.440  us/op
```
