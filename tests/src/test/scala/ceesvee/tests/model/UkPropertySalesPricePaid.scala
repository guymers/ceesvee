package ceesvee.tests.model

import ceesvee.CsvFieldDecoder
import ceesvee.CsvRecordDecoder

import java.time.LocalDate
import java.util.UUID

case class UkPropertySalesPricePaid(
  identifier: UkPropertySalesPricePaid.Identifier,
  price: Int,
  date: LocalDate,
  postcode: String,
  `type`: UkPropertySalesPricePaid.PropertyType,
  age: UkPropertySalesPricePaid.PropertyAge,
  tenure: UkPropertySalesPricePaid.PropertyTenure,
  address: UkPropertySalesPricePaid.Address,
  category: UkPropertySalesPricePaid.PropertyCategory,
  status: String,
)
object UkPropertySalesPricePaid {

  case class Identifier(value: UUID) extends AnyVal
  object Identifier {
    implicit val csvFieldDecoder: CsvFieldDecoder[Identifier] = {
      CsvFieldDecoder.instance { str =>
        val s = str.stripPrefix("{").stripSuffix("}")
        CsvFieldDecoder.uuid.decode(s)
      }.map(apply(_))
    }
  }

  sealed abstract class PropertyType(val value: String)
  object PropertyType {
    case object Detached extends PropertyType("D")
    case object SemiDetached extends PropertyType("S")
    case object Terraced extends PropertyType("T")
    case object Flats extends PropertyType("F")
    case object Other extends PropertyType("O")

    implicit val csvFieldDecoder: CsvFieldDecoder[PropertyType] = {
      CsvFieldDecoder.instance {
        case Detached.value => Right(Detached)
        case SemiDetached.value => Right(SemiDetached)
        case Terraced.value => Right(Terraced)
        case Flats.value => Right(Flats)
        case Other.value => Right(Other)
        case s => Left(CsvFieldDecoder.Error(s, "invalid property type"))
      }
    }
  }

  sealed abstract class PropertyAge(val value: String)
  object PropertyAge {
    case object New extends PropertyAge("Y")
    case object Established extends PropertyAge("N")

    implicit val csvFieldDecoder: CsvFieldDecoder[PropertyAge] = {
      CsvFieldDecoder.instance {
        case New.value => Right(New)
        case Established.value => Right(Established)
        case s => Left(CsvFieldDecoder.Error(s, "invalid property age"))
      }
    }
  }

  sealed abstract class PropertyTenure(val value: String)
  object PropertyTenure {
    case object Freehold extends PropertyTenure("F")
    case object Leasehold extends PropertyTenure("L")

    implicit val csvFieldDecoder: CsvFieldDecoder[PropertyTenure] = {
      CsvFieldDecoder.instance {
        case Freehold.value => Right(Freehold)
        case Leasehold.value => Right(Leasehold)
        case s => Left(CsvFieldDecoder.Error(s, "invalid property tenure"))
      }
    }
  }

  sealed abstract class PropertyCategory(val value: String)
  object PropertyCategory {
    case object Additional extends PropertyCategory("A")
    case object Leasehold extends PropertyCategory("B")

    implicit val csvFieldDecoder: CsvFieldDecoder[PropertyCategory] = {
      CsvFieldDecoder.instance {
        case Additional.value => Right(Additional)
        case Leasehold.value => Right(Leasehold)
        case s => Left(CsvFieldDecoder.Error(s, "invalid property category"))
      }
    }
  }

  case class Address(
    PAON: String,
    SAON: Option[String],
    road: String,
    locality: Option[String],
    townCity: String,
    district: String,
    county: String,
  )
  object Address {
    implicit val decoder: CsvRecordDecoder[Address] = CsvRecordDecoder.derived
  }

  implicit val decoderLocalDate: CsvFieldDecoder[LocalDate] = {
    CsvFieldDecoder.instance { str =>
      val s = str.take(10) // "2019-01-14 00:00"
      CsvFieldDecoder.localDate.decode(s)
    }
  }

  implicit val decoder: CsvRecordDecoder[UkPropertySalesPricePaid] = CsvRecordDecoder.derived
}
