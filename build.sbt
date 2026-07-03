// format: off

import sbt.util.CacheImplicits.given
import xsbti.HashedVirtualFileRef

val catsVersion = "2.13.0"
val fs2Version = "3.13.0"
val zioVersion = "2.1.19"

val Scala213 = "2.13.18"
val Scala3 = "3.3.8"

organization := "io.github.guymers"
homepage := Some(url("https://github.com/guymers/ceesvee"))
licenses := List(License.MIT)
developers := List(
  Developer("guymers", "Sam Guymer", "@guymers", url("https://github.com/guymers"))
)
ThisBuild / scmInfo := Some(ScmInfo(url("https://github.com/guymers/ceesvee"), "git@github.com:guymers/ceesvee.git"))

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
      "-Wnonunit-statement",
      "-Wunused:all",
      "-Wvalue-discard",
    )
  }),
  Test / scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq("-Wconf:cat=scala3-migration:silent")
    case _ => Seq.empty
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
      case Some((2, _)) => Seq(compilerPlugin(("org.typelevel" % "kind-projector" % "0.13.4").cross(CrossVersion.full)))
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

lazy val testDataResourceDirectory = settingKey[File]("Directory for downloaded real-world test data files")
lazy val testDataResources = taskKey[Seq[HashedVirtualFileRef]]("Downloaded real-world test data files")

ThisBuild / testDataResourceDirectory := rootOutputDirectory.value.resolve("test-data-resources").toFile

ThisBuild / testDataResources := Def.cachedTask {
  val converter = fileConverter.value
  val s = streams.value

  val dir = (ThisBuild / testDataResourceDirectory).value
  val files = TestFiles.Csv.toSeq.map  { case (f, (url, hash)) =>
    val file = dir / "csv" / f
    TestFiles.download(s.log, file, url, hash)
    file
  } ++ TestFiles.Tsv.map  { case (f, (url, hash)) =>
    val file = dir / "tsv" / f
    TestFiles.download(s.log, file, url, hash)
    file
  }

  files.map { file =>
    val vf = converter.toVirtualFile(file.toPath)
    Def.declareOutput(vf)
    vf: HashedVirtualFileRef
  }
}.value

lazy val tests = proj("tests", None)
  .settings(publish / skip := true)
  .settings(zioTestSettings)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % fs2Version % Test,
    ),
    Test / resourceGenerators += Def.task {
      val converter = fileConverter.value
      (ThisBuild / testDataResources).value.map(vf => converter.toPath(vf).toFile)
    }.taskValue,
    Test / managedResourceDirectories := Seq((ThisBuild / testDataResourceDirectory).value),
  )
  .dependsOn(core, fs2, zio)
  .disablePlugins(MimaPlugin)

Global / excludeLintKeys ++= Set(
  com.github.sbt.git.SbtGit.GitKeys.gitDescribedVersion,
  com.github.sbt.git.SbtGit.GitKeys.gitUncommittedChanges,
)
