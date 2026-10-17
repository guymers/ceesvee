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
    given csvFieldDecoder: CsvFieldDecoder[Identifier] = {
      CsvFieldDecoder.instance { str =>
        val s = str.stripPrefix("{").stripSuffix("}")
        CsvFieldDecoder.uuid.decode(s)
      }.map(apply(_))
    }
  }

  enum PropertyType(val value: String) {
    case Detached extends PropertyType("D")
    case SemiDetached extends PropertyType("S")
    case Terraced extends PropertyType("T")
    case Flats extends PropertyType("F")
    case Other extends PropertyType("O")
  }
  object PropertyType {
    given csvFieldDecoder: CsvFieldDecoder[PropertyType] = {
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

  enum PropertyAge(val value: String) {
    case New extends PropertyAge("Y")
    case Established extends PropertyAge("N")
  }
  object PropertyAge {
    given csvFieldDecoder: CsvFieldDecoder[PropertyAge] = {
      CsvFieldDecoder.instance {
        case New.value => Right(New)
        case Established.value => Right(Established)
        case s => Left(CsvFieldDecoder.Error(s, "invalid property age"))
      }
    }
  }

  enum PropertyTenure(val value: String) {
    case Freehold extends PropertyTenure("F")
    case Leasehold extends PropertyTenure("L")
  }
  object PropertyTenure {
    given csvFieldDecoder: CsvFieldDecoder[PropertyTenure] = {
      CsvFieldDecoder.instance {
        case Freehold.value => Right(Freehold)
        case Leasehold.value => Right(Leasehold)
        case s => Left(CsvFieldDecoder.Error(s, "invalid property tenure"))
      }
    }
  }

  enum PropertyCategory(val value: String) {
    case Additional extends PropertyCategory("A")
    case Leasehold extends PropertyCategory("B")
  }
  object PropertyCategory {
    given csvFieldDecoder: CsvFieldDecoder[PropertyCategory] = {
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
  ) derives CsvRecordDecoder

  given decoderLocalDate: CsvFieldDecoder[LocalDate] = {
    CsvFieldDecoder.instance { str =>
      val s = str.take(10) // "2019-01-14 00:00"
      CsvFieldDecoder.localDate.decode(s)
    }
  }

  given decoder: CsvRecordDecoder[UkPropertySalesPricePaid] = CsvRecordDecoder.derived
}
