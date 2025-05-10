// format: off

val catsVersion = "2.13.0"
val fs2Version = "3.12.2"
val zioVersion = "2.1.19"

val Scala213 = "2.13.16"
val Scala3 = "3.3.6"

inThisBuild(Seq(
  organization := "io.github.guymers",
  homepage := Some(url("https://github.com/guymers/ceesvee")),
  licenses := List(License.MIT),
  developers := List(
    Developer("guymers", "Sam Guymer", "@guymers", url("https://github.com/guymers"))
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/guymers/ceesvee"), "git@github.com:guymers/ceesvee.git")),
))

lazy val commonSettings = Seq(
  scalaVersion := Scala213,
  crossScalaVersions := Seq(Scala213, Scala3),
  versionScheme := Some("pvp"),

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-release", "17",
    "-unchecked",
  ),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "-explaintypes",
      "-language:existentials",
      "-language:higherKinds",
      "-Xsource:3",
    )
    case _ => Seq(
      "-explain",
      "-explain-types",
      "-no-indent",
      "-source:future",
    )
  }),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "-Vimplicits",
      "-Vtype-diffs",
      "-Wconf:cat=scala3-migration:silent",
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnonunit-statement",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wunused:_",
      "-Wperformance",
      "-Wvalue-discard",

      "-Xlint:_,-byname-implicit", // exclude byname-implicit https://github.com/scala/bug/issues/12072
    )
    case _ => Seq(
      "-Wconf:name=PatternMatchExhaustivity:error",
      "-Wnonunit-statement",
      "-Wunused:all",
      "-Wvalue-discard",
    )
  }),

  Compile / console / scalacOptions ~= filterScalacConsoleOpts,
  Test / console / scalacOptions ~= filterScalacConsoleOpts,

  Compile / compile / wartremoverErrors := Warts.all,
  Compile / compile / wartremoverErrors --= Seq(
    Wart.Any,
    Wart.Equals,
    Wart.FinalCaseClass,
    Wart.ImplicitParameter,
    Wart.Nothing,
  ),
  Test / compile / wartremoverErrors := Seq(
    Wart.NonUnitStatements,
    Wart.Null,
    Wart.Return,
  ),
)

def filterScalacConsoleOpts(options: Seq[String]) = {
  options.filterNot { opt =>
    opt == "-Xfatal-warnings" || opt.startsWith("-Xlint") || opt.startsWith("-W")
  }
}

lazy val zioTestSettings = Seq(
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio-test" % zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  ),
)

def proj(name: String, dir: Option[String]) =
  Project(name, file(dir.map(d => s"$d/$name").getOrElse(name)))
    .settings(moduleName := s"ceesvee-$name")
    .settings(commonSettings)

def module(name: String) = proj(name, Some("modules"))
  .settings(zioTestSettings)
  .settings(
    mimaPreviousArtifacts := previousStableVersion.value.map(organization.value %% moduleName.value % _).toSet,
  )

lazy val ceesvee = project.in(file("."))
  .settings(commonSettings)
  .settings(publish / skip := true)
  .aggregate(
    core, fs2, zio,
    benchmark,
  )
  .disablePlugins(MimaPlugin)

lazy val core = module("core")
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion % Optional,
      "org.typelevel" %% "cats-laws" % catsVersion % Test,
      "org.typelevel" %% "discipline-munit" % "2.0.0" % Test,
    ),
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(
        "com.softwaremill.magnolia1_2" %% "magnolia" % "1.1.10",
        "org.scala-lang" % "scala-reflect" % scalaVersion.value,
        "com.chuusai" %% "shapeless" % "2.3.13" % Test,
      )
      case _ => Seq.empty
    }),
  )

lazy val fs2 = module("fs2")
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "dev.zio" %% "zio-interop-cats" % "23.1.0.5" % Test,
    ),
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
      case _ => Seq.empty
    }),
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val zio = module("zio")
  .settings(
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % zioVersion,
      "dev.zio" %% "zio-streams" % zioVersion,
    ),
  )
  .dependsOn(core % "compile->compile;test->test")

lazy val benchmark = proj("benchmark", None)
  .settings(publish / skip := true)
  .settings(
    libraryDependencies ++= Seq(
      "com.univocity" % "univocity-parsers" % "2.9.1",
      "com.github.tototoshi" %% "scala-csv" % "2.0.0",
    ),
  )
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
  .disablePlugins(MimaPlugin, WartRemover)

Global / excludeLintKeys += benchmark / Compile / compile / wartremoverErrors


val TestCsvFiles = Map(
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
    "7c9cf6b70599b8ad54365171e5343273a5a91b04",
  ),
)

def downloadTestCsvFile(log: Logger, file: File): Unit = {
  import scala.sys.process.*

  val (_url, _hash) = TestCsvFiles(file.name)

  val currentHash = FileInfo.hash(file)
  val expected = FileInfo.hash(file, _hash.sliding(2, 2).map(Integer.parseInt(_, 16).toByte).toArray)

  if (currentHash != expected) {
    file.getParentFile.mkdirs()
    file.createNewFile()

    log.info(s"Downloading test CSV file: ${file.name}")

    url(_url) #> file !

    log.info(s"Downloaded test CSV file: ${file.name}")

    val hash = FileInfo.hash(file)
    if (hash != expected) {
      throw new IllegalStateException(s"Test CSV file ${file.name} does not match expected hash")
    }
  }
}

// only want to download the files once no matter how many cross Scala versions there are
def _testCsvFilesDir(base: File) = base / "target" / "scala" / "resource_managed" / "test"

lazy val tests = proj("tests", None)
  .settings(publish / skip := true)
  .settings(zioTestSettings)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % fs2Version % Test,
    ),
    Test / resourceGenerators += Def.task {
      val s = streams.value

      val dir = _testCsvFilesDir((Test / baseDirectory).value)
      val files = TestCsvFiles.keys.toSeq.map(dir / "csv" / _)
      files.foreach(downloadTestCsvFile(s.log, _))
      files
    }.taskValue,
    Test / managedResourceDirectories += _testCsvFilesDir((Test / baseDirectory).value),
  )
  .dependsOn(core, fs2, zio)
  .disablePlugins(MimaPlugin)
