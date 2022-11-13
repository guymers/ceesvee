package ceesvee

import java.net.URI
import java.time.*
import java.time.format.DateTimeParseException
import java.time.zone.ZoneRulesException
import java.util.UUID
import scala.util.control.NoStackTrace

trait CsvFieldDecoder[A] { self =>
  def decode(raw: String): Either[CsvFieldDecoder.Error, A]

  final def map[B](f: A => B): CsvFieldDecoder[B] = (raw: String) => {
    self.decode(raw).map(f(_))
  }

  final def emap[B](f: A => Either[String, B]): CsvFieldDecoder[B] = (raw: String) => {
    self.decode(raw).flatMap(a => f(a).left.map(CsvFieldDecoder.Error(raw, _)))
  }
}

object CsvFieldDecoder extends CsvFieldDecoder1 {

  case class Error(raw: String, reason: String)
    extends RuntimeException(s"Failed to decode ${raw.take(64)} because: $reason")
    with NoStackTrace

  def apply[A](implicit D: CsvFieldDecoder[A]): CsvFieldDecoder[A] = D

  def instance[A](f: String => Either[Error, A]): CsvFieldDecoder[A] = f(_)

  implicit val string: CsvFieldDecoder[String] = instance(Right(_))

  // "true" and "t" are true, "false" and "f" are false, anything else is an error
  implicit val boolean: CsvFieldDecoder[Boolean] = instance { str =>
    if (str == "true" || str == "t") {
      Right(true)
    } else if (str == "false" || str == "f") {
      Right(false)
    } else {
      Left(Error(str, "invalid boolean value"))
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
      case _: NumberFormatException => Left(Error(str, s"invalid $typeName value"))
    }
  }

  implicit val localDate: CsvFieldDecoder[LocalDate] = instanceDateTimeParse(LocalDate.parse(_))
  implicit val localDateTime: CsvFieldDecoder[LocalDateTime] = instanceDateTimeParse(LocalDateTime.parse(_))
  implicit val localTime: CsvFieldDecoder[LocalTime] = instanceDateTimeParse(LocalTime.parse(_))
  implicit val instant: CsvFieldDecoder[Instant] = instanceDateTimeParse(Instant.parse(_))
  implicit val offsetDateTime: CsvFieldDecoder[OffsetDateTime] = instanceDateTimeParse(OffsetDateTime.parse(_))
  implicit val zonedDateTime: CsvFieldDecoder[ZonedDateTime] = instanceDateTimeParse(ZonedDateTime.parse(_))

  private val DateTimeParseExceptionPrefix = "^Text '.*' could not be parsed: (.*)".r

  def instanceDateTimeParse[T](parse: String => T): CsvFieldDecoder[T] = instance { str =>
    try {
      Right(parse(str))
    } catch {
      case e: DateTimeParseException =>
        val reason = e.getMessage match {
          case DateTimeParseExceptionPrefix(msg) => msg
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
