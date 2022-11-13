package ceesvee

import magnolia1.CaseClass
import magnolia1.Magnolia

import scala.language.experimental.macros

trait CsvRecordEncoderDeriveScalaVersion { self: CsvRecordEncoder.type =>

  type Typeclass[T] = CsvRecordEncoder[T]

  def join[T](cc: CaseClass[Typeclass, T]): Typeclass[T] = new CsvRecordEncoder[T] {
    override val numFields = cc.parameters.foldLeft(0)(_ + _.typeclass.numFields)
    override def encode(t: T) = {
      val builder = IndexedSeq.newBuilder[String]
      builder.sizeHint(numFields)

      cc.parameters.foreach { p =>
        builder.addAll(p.typeclass.encode(p.dereference(t)))
      }

      builder.result()
    }
  }

  def derived[A]: CsvRecordEncoder[A] = macro Magnolia.gen[A]
}
