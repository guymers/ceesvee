package ceesvee

package object zio {

  type Error[E] = Either[E, CsvParser.Error]
}
