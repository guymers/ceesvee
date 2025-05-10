## Architecture

### Module Structure

- **core** - Main CSV parsing, encoding/decoding logic. Only dependency is an optional one on [cats](https://github.com/typelevel/cats)
- **fs2** - Integration with [fs2](https://github.com/typelevel/fs2) streams
- **zio** - Integration with [ZIO](https://github.com/zio/zio) streams
- **benchmark** - JMH performance benchmarks comparing against other CSV libraries
- **tests** - Integration tests with real-world CSV files
