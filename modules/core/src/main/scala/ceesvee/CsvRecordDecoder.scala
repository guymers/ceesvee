package ceesvee

import ceesvee.util.<:!<

import scala.collection.immutable.SortedMap
import scala.util.control.NoStackTrace

trait CsvRecordDecoder[A] { self =>
  def numFields: Int
  def decode(fields: IndexedSeq[String]): Either[CsvRecordDecoder.Errors, A]

  final def map[B](f: A => B): CsvRecordDecoder[B] = emap(a => Right(f(a)))

  final def emap[B](f: A => Either[String, B]): CsvRecordDecoder[B] = new CsvRecordDecoder[B] {
    override val numFields = self.numFields
    override def decode(fields: IndexedSeq[String]) = {
      self.decode(fields).flatMap { a =>
        f(a).left.map { msg =>
          // stick the error on the first column
          val errors = SortedMap(0 -> CsvRecordDecoder.Errors.Record(msg))
          CsvRecordDecoder.Errors(fields, errors)
        }
      }
    }
  }
}
object CsvRecordDecoder extends CsvRecordDecoder1 {

  final case class Errors(
    raw: Iterable[String],
    errors: SortedMap[Int, Errors.Error],
  ) extends RuntimeException({
      val reasons = errors.toList.map({ case (i, e) => s"index ${i.toString} ${e.toString}" })
      s"Failed to decode ${raw.mkString(",").take(64)} because: ${reasons.toString}"
    }) with NoStackTrace
  object Errors {

    sealed trait Error {
      override val toString: String = this match {
        case r: Record => r.toString
        case f: Field => f.toString
      }
    }

    final case class Record(error: String) extends Error {
      override val toString: String = error
    }

    sealed trait Field extends Error {
      override val toString: String = this match {
        case Field.Invalid(error) => error.getMessage
        case Field.Missing => "Missing field"
      }
    }
    object Field {
      final case class Invalid(error: CsvFieldDecoder.Error) extends Field
      case object Missing extends Field
    }
  }

  def apply[T](implicit D: CsvRecordDecoder[T]): CsvRecordDecoder[T] = D

  private[ceesvee] def createField[T](implicit D: => CsvFieldDecoder[T]): CsvRecordDecoder[T] = {
    new CsvRecordDecoder[T] {
      override val numFields = 1
      @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
      override def decode(fields: IndexedSeq[String]) = {
        if (fields.isEmpty) Left(Errors(fields, SortedMap(0 -> Errors.Field.Missing)))
        else {
          D.decode(fields.head).left.map { err =>
            Errors(fields, SortedMap(0 -> Errors.Field.Invalid(err)))
          }
        }
      }
    }
  }

  private[ceesvee] def createFieldOptional[T](implicit D: => CsvFieldDecoder[T]): CsvRecordDecoder[Option[T]] = {
    new CsvRecordDecoder[Option[T]] {
      override val numFields = 1
      @SuppressWarnings(Array("org.wartremover.warts.IterableOps"))
      override def decode(fields: IndexedSeq[String]) = {
        if (fields.isEmpty) Left(Errors(fields, SortedMap(0 -> Errors.Field.Missing)))
        else if (isNone(fields.head)) Right(None)
        else {
          D.decode(fields.head).map(Some(_)).left.map { err =>
            Errors(fields, SortedMap(0 -> Errors.Field.Invalid(err)))
          }
        }
      }
    }
  }

  // an empty string is considered `None`
  def isNone(str: String): Boolean = str.isEmpty
}

// cache common field record decoders
sealed trait CsvRecordDecoder1 extends CsvRecordDecoder2 { self: CsvRecordDecoder.type =>

  implicit val fieldString: CsvRecordDecoder[String] = createField[String]
  implicit val fieldBoolean: CsvRecordDecoder[Boolean] = createField[Boolean]
  implicit val fieldInt: CsvRecordDecoder[Int] = createField[Int]
  implicit val fieldLong: CsvRecordDecoder[Long] = createField[Long]
  implicit val fieldFloat: CsvRecordDecoder[Float] = createField[Float]
  implicit val fieldDouble: CsvRecordDecoder[Double] = createField[Double]

  implicit val fieldOptionalString: CsvRecordDecoder[Option[String]] = createFieldOptional[String]
  implicit val fieldOptionalBoolean: CsvRecordDecoder[Option[Boolean]] = createFieldOptional[Boolean]
  implicit val fieldOptionalInt: CsvRecordDecoder[Option[Int]] = createFieldOptional[Int]
  implicit val fieldOptionalLong: CsvRecordDecoder[Option[Long]] = createFieldOptional[Long]
  implicit val fieldOptionalFloat: CsvRecordDecoder[Option[Float]] = createFieldOptional[Float]
  implicit val fieldOptionalDouble: CsvRecordDecoder[Option[Double]] = createFieldOptional[Double]
}

sealed trait CsvRecordDecoder2 extends CsvRecordDecoder3 { self: CsvRecordDecoder.type =>

  implicit def field[T: CsvFieldDecoder]: CsvRecordDecoder[T] = createField[T]
  implicit def fieldOptional[T: CsvFieldDecoder](implicit ev: T <:!< Option[?]): CsvRecordDecoder[Option[T]] = {
    val _ = ev
    createFieldOptional[T]
  }
}

sealed trait CsvRecordDecoder3 extends CsvRecordDecoderDeriveScalaVersion { self: CsvRecordDecoder.type =>

  implicit def optional[T](implicit D: CsvRecordDecoder[T], ev: T <:!< Option[?]): CsvRecordDecoder[Option[T]] = {
    val _ = ev

    new CsvRecordDecoder[Option[T]] {
      override val numFields = D.numFields
      @SuppressWarnings(Array("org.wartremover.warts.Null"))
      override def decode(fields: IndexedSeq[String]) = {
        val allNone = (1 to numFields).zipAll(fields, -1, null).forall { case (_, field) =>
          field != null && CsvRecordDecoder.isNone(field)
        }

        if (allNone) Right(None) else D.decode(fields).map(Some(_))
      }
    }
  }
}
