package ceesvee

import java.net.URI
import java.time.*
import java.time.format.DateTimeParseException
import java.time.zone.ZoneRulesException
import java.util.Locale
import java.util.UUID
import scala.collection.immutable.SortedSet
import scala.util.control.NoStackTrace

trait CsvFieldDecoder[A] { self =>
  def decode(raw: String): Either[CsvFieldDecoder.Error, A]

  final def map[B](f: A => B): CsvFieldDecoder[B] = (raw: String) => {
    self.decode(raw).map(f(_))
  }

  final def emap[B](f: A => Either[String, B]): CsvFieldDecoder[B] = (raw: String) => {
    self.decode(raw).flatMap(a => f(a).left.map(CsvFieldDecoder.Error(raw, _)))
  }

  final def eemap[B](f: A => Either[CsvFieldDecoder.Error, B]): CsvFieldDecoder[B] = (raw: String) => {
    self.decode(raw).flatMap(f(_))
  }
}

object CsvFieldDecoder extends CsvFieldDecoder1 {

  case class Error(raw: String, reason: String)
    extends RuntimeException(s"Failed to decode ${raw.take(64)} because: $reason")
    with NoStackTrace

  def apply[A](implicit D: CsvFieldDecoder[A]): CsvFieldDecoder[A] = D

  def instance[A](f: String => Either[Error, A]): CsvFieldDecoder[A] = f(_)

  implicit val string: CsvFieldDecoder[String] = instance(Right(_))

  // "true", "t", "yes", "y" are true
  // "false", "f", "no", "n" are false
  // anything else is an error
  implicit val boolean: CsvFieldDecoder[Boolean] = {
    val trues = SortedSet("true", "t", "yes", "y")
    val falses = SortedSet("false", "f", "no", "n")

    val truesStr = trues.map(s => s"'$s'").mkString(",")
    val falsesStr = falses.map(s => s"'$s'").mkString(",")

    instance { str =>
      val lower = str.toLowerCase(Locale.ROOT)
      if (trues.contains(lower)) Right(true)
      else if (falses.contains(lower)) Right(false)
      else Left(Error(str, s"invalid boolean value valid values are $truesStr and $falsesStr"))
    }
  }

  implicit val int: CsvFieldDecoder[Int] = instanceNumberFormat("int")(_.toInt)
  implicit val long: CsvFieldDecoder[Long] = instanceNumberFormat("long")(_.toLong)
  implicit val float: CsvFieldDecoder[Float] = instanceNumberFormat("float")(_.toFloat)
  implicit val double: CsvFieldDecoder[Double] = instanceNumberFormat("double")(_.toDouble)
  def instanceNumberFormat[T](typeName: String)(to: String => T): CsvFieldDecoder[T] = instance { str =>
    try {
      Right(to(str))
    } catch {
      case _: NumberFormatException => Left(Error(str, s"invalid numeric value, required $typeName"))
    }
  }

  implicit val localDate: CsvFieldDecoder[LocalDate] =
    instanceDateTimeParse("date", "2021-12-03")(LocalDate.parse(_))
  implicit val localDateTime: CsvFieldDecoder[LocalDateTime] =
    instanceDateTimeParse("date time", "2021-12-03T10:15:30")(LocalDateTime.parse(_))
  implicit val localTime: CsvFieldDecoder[LocalTime] =
    instanceDateTimeParse("time", "10:15:30")(LocalTime.parse(_))
  implicit val instant: CsvFieldDecoder[Instant] =
    instanceDateTimeParse("instant", "2021-12-03T10:15:30.00Z")(Instant.parse(_))
  implicit val offsetDateTime: CsvFieldDecoder[OffsetDateTime] =
    instanceDateTimeParse("date time with offset", "2021-12-03T10:15:30+01:00")(OffsetDateTime.parse(_))
  implicit val zonedDateTime: CsvFieldDecoder[ZonedDateTime] =
    instanceDateTimeParse("date time with timezone", "2021-12-03T10:15:30+01:00[Europe/Paris]")(ZonedDateTime.parse(_))

  private val DateTimeParseSpecificExceptionPrefix = "^Text '.*' could not be parsed: (.*)".r
  private val DateTimeParseExceptionPrefix = "^Text '.*' could not be parsed(.*)".r

  def instanceDateTimeParse[T](typeName: String, example: String)(parse: String => T): CsvFieldDecoder[T] =
    instance { str =>
      try {
        Right(parse(str))
      } catch {
        case e: DateTimeParseException =>
          val reason = e.getMessage match {
            case DateTimeParseSpecificExceptionPrefix(msg) => msg
            case DateTimeParseExceptionPrefix(_) => s"invalid $typeName, expected a value such as $example"
            case msg => msg
          }

          Left(Error(str, reason))
      }
    }

  implicit val zoneId: CsvFieldDecoder[ZoneId] = instance { str =>
    try {
      Right(ZoneId.of(str))
    } catch {
      case e: ZoneRulesException => Left(Error(str, e.getMessage))
      case e: DateTimeException => Left(Error(str, e.getMessage))
    }
  }

  implicit val uri: CsvFieldDecoder[URI] = instanceIllegalArgument(URI.create(_))
  implicit val uuid: CsvFieldDecoder[UUID] = instanceIllegalArgument(UUID.fromString(_))

  def instanceIllegalArgument[T](parse: String => T): CsvFieldDecoder[T] = instance { str =>
    try {
      Right(parse(str))
    } catch {
      case e: IllegalArgumentException => Left(Error(str, e.getMessage))
    }
  }
}

sealed trait CsvFieldDecoder1 { self: CsvFieldDecoder.type =>

  implicit def numeric[T](implicit N: Numeric[T]): CsvFieldDecoder[T] = instance { str =>
    N.parseString(str).toRight(Error(str, "invalid numeric value"))
  }
}
