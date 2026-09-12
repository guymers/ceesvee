
## Build Commands

- `sbt compile` -> compile code
- `sbt Test/compile` -> compile test code
- `sbt scalafmtAll` -> format all code
- `sbt test` -> run unit tests
- `sbt testFull` -> run all unit tests
- `sbt testOnly <fully qualified class name>` -> run tests in the given class
- `sbt tests/test` -> run tests that use real world files

### sbt Usage

- run one command per `sbt` call
- sbt 2 caches aggressively, if a command returns successfully everything is up to date
