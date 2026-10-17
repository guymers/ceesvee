package ceesvee

import java.net.URI
import java.time.*
import java.util.UUID

trait CsvFieldEncoder[A] { self =>

  /**
   * Encode a value into a string.
   *
   * Escaping and quoting is handled separately.
   */
  def encode(a: A): String

  final def contramap[B](f: B => A): CsvFieldEncoder[B] = (b: B) => {
    self.encode(f(b))
  }
}

object CsvFieldEncoder {

  def apply[A](using D: CsvFieldEncoder[A]): CsvFieldEncoder[A] = D

  def instance[A](f: A => String): CsvFieldEncoder[A] = f(_)

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def fromToString[A]: CsvFieldEncoder[A] = instance(_.toString)

  given string: CsvFieldEncoder[String] = instance(identity(_))

  given boolean: CsvFieldEncoder[Boolean] = instance {
    case true => "true"
    case false => "false"
  }

  given int: CsvFieldEncoder[Int] = fromToString
  given long: CsvFieldEncoder[Long] = fromToString
  given float: CsvFieldEncoder[Float] = fromToString
  given double: CsvFieldEncoder[Double] = fromToString
  given bigDecimal: CsvFieldEncoder[BigDecimal] = fromToString

  given localDate: CsvFieldEncoder[LocalDate] = fromToString
  given localDateTime: CsvFieldEncoder[LocalDateTime] = fromToString
  given localTime: CsvFieldEncoder[LocalTime] = fromToString
  given instant: CsvFieldEncoder[Instant] = fromToString
  given offsetDateTime: CsvFieldEncoder[OffsetDateTime] = fromToString
  given zonedDateTime: CsvFieldEncoder[ZonedDateTime] = fromToString
  given zoneId: CsvFieldEncoder[ZoneId] = instance(_.getId)

  given uri: CsvFieldEncoder[URI] = fromToString
  given uuid: CsvFieldEncoder[UUID] = fromToString
}
