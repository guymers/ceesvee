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

  def apply[A](implicit D: CsvFieldEncoder[A]): CsvFieldEncoder[A] = D

  def instance[A](f: A => String): CsvFieldEncoder[A] = f(_)

  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def fromToString[A]: CsvFieldEncoder[A] = instance(_.toString)

  implicit val string: CsvFieldEncoder[String] = instance(identity(_))

  implicit val boolean: CsvFieldEncoder[Boolean] = instance {
    case true => "true"
    case false => "false"
  }

  implicit val int: CsvFieldEncoder[Int] = fromToString
  implicit val long: CsvFieldEncoder[Long] = fromToString
  implicit val float: CsvFieldEncoder[Float] = fromToString
  implicit val double: CsvFieldEncoder[Double] = fromToString
  implicit val bigDecimal: CsvFieldEncoder[BigDecimal] = fromToString

  implicit val localDate: CsvFieldEncoder[LocalDate] = fromToString
  implicit val localDateTime: CsvFieldEncoder[LocalDateTime] = fromToString
  implicit val localTime: CsvFieldEncoder[LocalTime] = fromToString
  implicit val instant: CsvFieldEncoder[Instant] = fromToString
  implicit val offsetDateTime: CsvFieldEncoder[OffsetDateTime] = fromToString
  implicit val zonedDateTime: CsvFieldEncoder[ZonedDateTime] = fromToString
  implicit val zoneId: CsvFieldEncoder[ZoneId] = instance(_.getId)

  implicit val uri: CsvFieldEncoder[URI] = fromToString
  implicit val uuid: CsvFieldEncoder[UUID] = fromToString
}
