package ceesvee

import ceesvee.util.<:!<

import scala.collection.immutable.SortedMap
import scala.util.control.NoStackTrace

trait CsvRecordDecoder[A] { self =>
  def numFields: Int
  def decode(fields: IndexedSeq[String]): Either[CsvRecordDecoder.Errors, A]

  /**
   * Decode `length` fields starting at `offset`.
   *
   * The default implementation copies the slice. Implementations that would
   * otherwise copy on every field should override this.
   */
  private[ceesvee] def decodeAt(
    fields: IndexedSeq[String],
    offset: Int,
    length: Int,
  ): Either[CsvRecordDecoder.Errors, A] = decode(fields.slice(offset, offset + length))

  final def map[B](f: A => B): CsvRecordDecoder[B] = emap(a => Right(f(a)))

  /**
   * Map the decoded value, reporting a failure against the whole record rather
   * than against a single field.
   */
  final def emap[B](f: A => Either[String, B]): CsvRecordDecoder[B] = new CsvRecordDecoder[B] {
    override val numFields = self.numFields
    override def decode(fields: IndexedSeq[String]) = {
      self.decode(fields).flatMap(a => f(a).left.map(recordError(_, fields)))
    }
    override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
      self.decodeAt(fields, offset, length).flatMap(a => f(a).left.map(recordError(_, fields)))
    }

    private def recordError(reason: String, fields: IndexedSeq[String]) =
      CsvRecordDecoder.Errors(
        fields,
        SortedMap.empty[Int, CsvRecordDecoder.Errors.Field],
        List(CsvRecordDecoder.Errors.Record(reason)),
      )
  }

  /**
   * Map the decoded value, reporting a failure against the given field index.
   *
   * Use [[emap]] to report a failure against the whole record.
   */
  final def emapAtIndex[B](f: A => Either[(Int, String), B]): CsvRecordDecoder[B] = new CsvRecordDecoder[B] {
    override val numFields = self.numFields
    override def decode(fields: IndexedSeq[String]) = {
      self.decode(fields).flatMap(a => f(a).left.map(fieldError(_, fields)))
    }
    override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
      self.decodeAt(fields, offset, length).flatMap(a => f(a).left.map(fieldError(_, fields)))
    }

    private def fieldError(error: (Int, String), fields: IndexedSeq[String]) = {
      val (index, reason) = error
      val fieldError = CsvRecordDecoder.Errors.Field.Custom(reason)
      CsvRecordDecoder.Errors(fields, SortedMap(index -> fieldError), Nil)
    }
  }

  final def ap[B](ff: CsvRecordDecoder[A => B]): CsvRecordDecoder[B] = new CsvRecordDecoder[B] {
    override val numFields = ff.numFields + self.numFields
    override def decode(fields: IndexedSeq[String]) = decodeAt(fields, 0, fields.length)
    override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
      ff.decodeAt(fields, offset, ff.numFields).flatMap { aToB =>
        self.decodeAt(fields, offset + ff.numFields, self.numFields) match {
          case Left(error) => Left(error.shiftFieldIndices(ff.numFields))
          case Right(a) => Right(aToB(a))
        }
      }
    }
  }

  final def map2[B, Z](fb: CsvRecordDecoder[B])(f: (A, B) => Z): CsvRecordDecoder[Z] = new CsvRecordDecoder[Z] {
    override val numFields = self.numFields + fb.numFields
    override def decode(fields: IndexedSeq[String]) = decodeAt(fields, 0, fields.length)
    override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
      self.decodeAt(fields, offset, self.numFields).flatMap { a =>
        fb.decodeAt(fields, offset + self.numFields, fb.numFields) match {
          case Left(error) => Left(error.shiftFieldIndices(self.numFields))
          case Right(b) => Right(f(a, b))
        }
      }
    }
  }

  final def product[B](fb: CsvRecordDecoder[B]): CsvRecordDecoder[(A, B)] = map2(fb)({ case t @ (_, _) => t })
}
object CsvRecordDecoder extends CsvRecordDecoder1 {

  /**
   * @param raw
   *   the raw record
   * @param errors
   *   errors against individual fields, keyed by the field index
   * @param records
   *   errors against the whole record
   */
  final case class Errors(
    raw: Iterable[String],
    errors: SortedMap[Int, Errors.Field],
    records: List[Errors.Record],
  ) extends RuntimeException with NoStackTrace {
    private lazy val message = {
      val recordReasons = records.map(e => s"record ${e.toString}")
      val fieldReasons = errors.toList.map({ case (i, e) => s"index ${i.toString} ${e.toString}" })
      s"Failed to decode ${raw.mkString(",").take(64)} because: ${(recordReasons ++ fieldReasons).mkString(";")}"
    }

    override def getMessage = message

    private[ceesvee] def shiftFieldIndices(offset: Int) = copy(
      errors = errors.map { case (i, error) => (i + offset, error) },
    )
  }
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
        case Field.Custom(error) => error
        case Field.Missing => "Missing field"
      }
    }
    object Field {
      final case class Invalid(error: CsvFieldDecoder.Error) extends Field
      final case class Custom(error: String) extends Field
      case object Missing extends Field
    }
  }

  def apply[T](using D: CsvRecordDecoder[T]): CsvRecordDecoder[T] = D

  private[ceesvee] def createField[T](using D: => CsvFieldDecoder[T]): CsvRecordDecoder[T] = {
    new CsvRecordDecoder[T] {
      override val numFields = 1

      override def decode(fields: IndexedSeq[String]) = decodeAt(fields, 0, fields.length)

      @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
      override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
        if (offset >= fields.length) Left(Errors(fields, SortedMap(0 -> Errors.Field.Missing), Nil))
        else {
          D.decode(fields(offset)).left.map { err =>
            Errors(fields, SortedMap(0 -> Errors.Field.Invalid(err)), Nil)
          }
        }
      }
    }
  }

  private[ceesvee] def createFieldOptional[T](using D: => CsvFieldDecoder[T]): CsvRecordDecoder[Option[T]] = {
    new CsvRecordDecoder[Option[T]] {
      override val numFields = 1

      override def decode(fields: IndexedSeq[String]) = decodeAt(fields, 0, fields.length)

      @SuppressWarnings(Array("org.wartremover.warts.SeqApply"))
      override private[ceesvee] def decodeAt(fields: IndexedSeq[String], offset: Int, length: Int) = {
        if (offset >= fields.length) Left(Errors(fields, SortedMap(0 -> Errors.Field.Missing), Nil))
        else if (isNone(fields(offset))) Right(None)
        else {
          D.decode(fields(offset)).map(Some(_)).left.map { err =>
            Errors(fields, SortedMap(0 -> Errors.Field.Invalid(err)), Nil)
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

  given fieldString: CsvRecordDecoder[String] = createField[String]
  given fieldBoolean: CsvRecordDecoder[Boolean] = createField[Boolean]
  given fieldInt: CsvRecordDecoder[Int] = createField[Int]
  given fieldLong: CsvRecordDecoder[Long] = createField[Long]
  given fieldFloat: CsvRecordDecoder[Float] = createField[Float]
  given fieldDouble: CsvRecordDecoder[Double] = createField[Double]

  given fieldOptionalString: CsvRecordDecoder[Option[String]] = createFieldOptional[String]
  given fieldOptionalBoolean: CsvRecordDecoder[Option[Boolean]] = createFieldOptional[Boolean]
  given fieldOptionalInt: CsvRecordDecoder[Option[Int]] = createFieldOptional[Int]
  given fieldOptionalLong: CsvRecordDecoder[Option[Long]] = createFieldOptional[Long]
  given fieldOptionalFloat: CsvRecordDecoder[Option[Float]] = createFieldOptional[Float]
  given fieldOptionalDouble: CsvRecordDecoder[Option[Double]] = createFieldOptional[Double]
}

sealed trait CsvRecordDecoder2 extends CsvRecordDecoder3 { self: CsvRecordDecoder.type =>

  given field[T](using CsvFieldDecoder[T]): CsvRecordDecoder[T] = createField[T]
  given fieldOptional[T](using CsvFieldDecoder[T], T <:!< Option[?]): CsvRecordDecoder[Option[T]] =
    createFieldOptional[T]
}

sealed trait CsvRecordDecoder3 extends CsvRecordDecoder4 { self: CsvRecordDecoder.type =>

  given optional[T](using D: CsvRecordDecoder[T], ev: T <:!< Option[?]): CsvRecordDecoder[Option[T]] = {
    new CsvRecordDecoder[Option[T]] {
      override val numFields = D.numFields
      @SuppressWarnings(Array(
        "org.wartremover.warts.SeqApply",
        "org.wartremover.warts.Var",
        "org.wartremover.warts.While",
      ))
      override def decode(fields: IndexedSeq[String]) = {
        var i = 0
        var allNone = fields.lengthIs >= numFields

        while (i < fields.length && allNone) {
          if (CsvRecordDecoder.isNone(fields(i))) {
            i += 1
          } else {
            allNone = false
          }
        }

        if (allNone) Right(None) else D.decode(fields).map(Some(_))
      }
    }
  }
}

sealed trait CsvRecordDecoder4 extends CsvRecordDecoderDerive { self: CsvRecordDecoder.type =>

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  given ApplyCsvRecordDecoder[F[_[_]]](using CatsApply[F]): F[CsvRecordDecoder] = {
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
  @inline given get: CatsApply[cats.Apply] = null
}
