package object ceesvee {

  // need to help out Scala 3
  implicit val invariantE: cats.Invariant[CsvRecordEncoder] =
    implicitly[cats.ContravariantSemigroupal[CsvRecordEncoder]]
  implicit val semigroupalE: cats.Semigroupal[CsvRecordEncoder] =
    implicitly[cats.ContravariantSemigroupal[CsvRecordEncoder]]

  implicit val invariantD: cats.Invariant[CsvRecordDecoder] = implicitly[cats.Apply[CsvRecordDecoder]]
  implicit val semigroupalD: cats.Semigroupal[CsvRecordDecoder] = implicitly[cats.Apply[CsvRecordDecoder]]
}
