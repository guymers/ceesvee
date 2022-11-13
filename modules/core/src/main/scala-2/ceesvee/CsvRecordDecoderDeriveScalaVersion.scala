package ceesvee

import magnolia1.CaseClass
import magnolia1.Magnolia

import scala.collection.immutable.ArraySeq
import scala.collection.immutable.SortedMap
import scala.language.experimental.macros

trait CsvRecordDecoderDeriveScalaVersion { self: CsvRecordDecoder.type =>

  type Typeclass[T] = CsvRecordDecoder[T]

  def join[T](cc: CaseClass[Typeclass, T]): Typeclass[T] = new CsvRecordDecoder[T] {
    override val numFields = cc.parameters.foldLeft(0)(_ + _.typeclass.numFields)
    override def decode(fields: IndexedSeq[String]) = {
      val errs = SortedMap.newBuilder[Int, Errors.Error]
      val values = Array.ofDim[Any](cc.parameters.length)

      val _ = cc.parameters.foldLeft((0, 0)) { case ((index, offset), p) =>
        val num = p.typeclass.numFields
        p.typeclass.decode(fields.slice(offset, offset + num)) match {
          case Left(error) => error.errors.foreach { case (k, v) => errs.addOne((k + offset, v)) }
          case Right(v) => values.update(index, v)
        }
        (index + 1, offset + num)
      }

      val errs_ = errs.result()
      if (errs_.nonEmpty) Left(Errors(fields, errs_))
      else Right(cc.rawConstruct(ArraySeq.unsafeWrapArray(values)))
    }
  }

  def derived[A]: CsvRecordDecoder[A] = macro Magnolia.gen[A]
}
