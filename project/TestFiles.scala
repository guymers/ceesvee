import sbt.*

object TestFiles {

  val Csv = Map(
    // https://www.stats.govt.nz/large-datasets/csv-files-for-download/
    "nz-greenhouse-gas-emissions-2019.csv" -> (
      "https://www.stats.govt.nz/assets/Uploads/Greenhouse-gas-emissions-industry-and-household/Greenhouse-gas-emissions-industry-and-household-Year-ended-2019/Download-data/Greenhouse-gas-emissions-industry-and-household-year-ended-2019-csv.csv",
      "2561a652157c5eb8f23a7f84f3648440fe67ba8d",
    ),

    // https://data.gov.uk/dataset/48c917d5-11a0-429f-a0db-0c5ae6ffa1c8/places-to-visit-in-causeway-coast-and-glens
    "uk-causeway-coast-and-glens.csv" -> (
      "https://ccgbcodni-cbcni.opendata.arcgis.com/datasets/42b6ad70a304442dbdb963974d44b433_0.csv",
      "5a15f2bf5861b34f985da88b33523f18aba10c08",
    ),

    // https://www.gov.uk/government/statistical-data-sets/price-paid-data-downloads
    "uk-property-sales-price-paid-2019.csv" -> (
      "http://prod.publicdata.landregistry.gov.uk.s3-website-eu-west-1.amazonaws.com/pp-2019.csv",
      "406fe6d20ea0ef4e21b693ac961650121e508e5e",
    ),
  )

  val Tsv = Map(
    // https://github.com/schappim/australian-postcodes
    "australian-postcodes.tsv" -> (
      "https://raw.githubusercontent.com/schappim/australian-postcodes/refs/tags/v2026.5.27/australian-postcodes-2021-04-23.tsv",
      "5a4f6888e5f5cd9b3e632b6ed63756a081e26ec1",
    ),
  )

  def download(log: Logger, file: File, _url: String, _hash: String): Unit = {
    val currentHash = FileInfo.hash(file)
    val expected = FileInfo.hash(file, _hash.sliding(2, 2).map(Integer.parseInt(_, 16).toByte).toArray)

    if (currentHash != expected) {
      file.getParentFile.mkdirs()
      file.createNewFile()

      log.info(s"Downloading test file: ${file.name}")

      sbt.io.Using.urlInputStream(url(_url).toURL) { in =>
        sbt.io.IO.transfer(in, file)
      }

      log.info(s"Downloaded test file: ${file.name}")

      val hash = FileInfo.hash(file)
      if (hash != expected) {
        throw new IllegalStateException(s"Test file ${file.name} does not match expected hash")
      }
    }
  }
}
