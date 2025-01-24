package ceesvee

import ceesvee.util.<:!<

import scala.collection.immutable.SortedMap
import scala.util.control.NoStackTrace

trait CsvRecordDecoder[A] { self =>
  def numFields: Int
  def decode(fields: IndexedSeq[String]): Either[CsvRecordDecoder.Errors, A]

  final def map[B](f: A => B): CsvRecordDecoder[B] = emap(a => Right(f(a)))

  final def emap[B](f: A => Either[String, B]): CsvRecordDecoder[B] = emapAtIndex(a => f(a).left.map((0, _)))

  final def emapAtIndex[B](f: A => Either[(Int, String), B]): CsvRecordDecoder[B] = new CsvRecordDecoder[B] {
    override val numFields = self.numFields
    override def decode(fields: IndexedSeq[String]) = {
      self.decode(fields).flatMap { a =>
        f(a).left.map { case (i, msg) =>
          val errors = SortedMap(i -> CsvRecordDecoder.Errors.Record(msg))
          CsvRecordDecoder.Errors(fields, errors)
        }
      }
    }
  }

  final def ap[B](ff: CsvRecordDecoder[A => B]): CsvRecordDecoder[B] = new CsvRecordDecoder[B] {
    override val numFields = ff.numFields + self.numFields
    override def decode(fields: IndexedSeq[String]) = for {
      aToB <- ff.decode(fields.take(ff.numFields))
      a <- self.decode(fields.drop(ff.numFields))
    } yield aToB(a)
  }

  final def map2[B, Z](fb: CsvRecordDecoder[B])(f: (A, B) => Z): CsvRecordDecoder[Z] = new CsvRecordDecoder[Z] {
    override val numFields = self.numFields + fb.numFields
    override def decode(fields: IndexedSeq[String]) = for {
      a <- self.decode(fields.take(self.numFields))
      b <- fb.decode(fields.drop(self.numFields))
    } yield f(a, b)
  }

  final def product[B](fb: CsvRecordDecoder[B]): CsvRecordDecoder[(A, B)] = map2(fb)({ case t @ (_, _) => t })
}
object CsvRecordDecoder extends CsvRecordDecoder1 {

  final case class Errors(
    raw: Iterable[String],
    errors: SortedMap[Int, Errors.Error],
  ) extends RuntimeException({
      val reasons = errors.toList.map({ case (i, e) => s"index ${i.toString} ${e.toString}" })
      s"Failed to decode ${raw.mkString(",").take(64)} because: ${reasons.mkString(";")}"
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

  implicit def field[T](implicit D: CsvFieldDecoder[T]): CsvRecordDecoder[T] = createField[T]
  implicit def fieldOptional[T](implicit D: CsvFieldDecoder[T], ev: T <:!< Option[?]): CsvRecordDecoder[Option[T]] = {
    val _ = ev
    createFieldOptional[T]
  }
}

sealed trait CsvRecordDecoder3 extends CsvRecordDecoder4 { self: CsvRecordDecoder.type =>

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

sealed trait CsvRecordDecoder4 extends CsvRecordDecoderDeriveScalaVersion { self: CsvRecordDecoder.type =>

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  implicit def ApplyCsvRecordDecoder[F[_[_]]](implicit env: CatsApply[F]): F[CsvRecordDecoder] = {
    val _ = env

    new cats.Apply[CsvRecordDecoder] {
      override def map[A, B](fa: CsvRecordDecoder[A])(f: A => B) = fa.map(f)
      override def ap[A, B](ff: CsvRecordDecoder[A => B])(fa: CsvRecordDecoder[A]) = fa.ap(ff)
      override def map2[A, B, Z](fa: CsvRecordDecoder[A], fb: CsvRecordDecoder[B])(f: (A, B) => Z) = fa.map2(fb)(f)
      override def product[A, B](fa: CsvRecordDecoder[A], fb: CsvRecordDecoder[B]) = fa.product(fb)
    }.asInstanceOf[F[CsvRecordDecoder]]
  }
}

// https://blog.7mind.io/no-more-orphans.html
final abstract class CatsApply[F[_[_]]]
object CatsApply {
  @SuppressWarnings(Array("org.wartremover.warts.Null"))
  @inline implicit final def get: CatsApply[cats.Apply] = null
}
