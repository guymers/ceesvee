package ceesvee.zio

import ceesvee.CsvParser

type Error[E] = Either[E, CsvParser.Error]
