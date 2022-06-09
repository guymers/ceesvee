// format: off

val shapelessVersion = "2.3.9"
val zioVersion = "1.0.14"

// if changing these also change versions in .github/workflows/ci.yml
val Scala213 = "2.13.8"
val Scala3 = "3.1.2"

inThisBuild(Seq(
  organization := "com.github.guymers",
  homepage := Some(url("https://github.com/guymers/ceesvee")),
  licenses := List(License.MIT),
  developers := List(
    Developer("guymers", "Sam Guymer", "@guymers", url("https://github.com/guymers"))
  ),
  scmInfo := Some(ScmInfo(url("https://github.com/sbt/ceesvee"), "git@github.com:guymers/ceesvee.git")),
))

lazy val commonSettings = Seq(
  scalaVersion := Scala213,
  crossScalaVersions := Seq(Scala213, Scala3),
  versionScheme := Some("early-semver"),

  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
  ),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, _)) => Seq(
      "-explaintypes",
      "-language:existentials",
      "-language:higherKinds",
      "-Xsource:3",
    )
    case Some((3, _)) => Seq(
      "-explain-types",
    )
    case _ => Seq.empty
  }),
  scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, minor)) if minor >= 13 => Seq(
      "-Vimplicits",
      "-Vtype-diffs",
      "-Wdead-code",
      "-Wextra-implicit",
      "-Wnumeric-widen",
      "-Woctal-literal",
      "-Wunused:_",
      "-Wvalue-discard",

      "-Xlint:_,-byname-implicit", // exclude byname-implicit https://github.com/scala/bug/issues/12072
    )
    case _ => Seq.empty
  }),

  Compile / console / scalacOptions ~= filterScalacConsoleOpts,
  Test / console / scalacOptions ~= filterScalacConsoleOpts,

  Compile / compile / wartremoverErrors := Warts.all,
  Compile / compile / wartremoverErrors --= Seq(
    Wart.Any,
    Wart.Equals,
    Wart.FinalCaseClass,
    Wart.Nothing
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

lazy val noPublishSettings = Seq(
  publish / skip := true,
)

lazy val zioTestSettings = Seq(
  libraryDependencies ++= Seq(
    "dev.zio" %% "zio-test" % zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
)

def proj(name: String, dir: Option[String]) =
  Project(name, file(dir.map(d => s"$d/$name").getOrElse(name)))
    .settings(moduleName := s"ceesvee-$name")
    .settings(commonSettings)

def module(name: String) = proj(name, Some("modules"))
  .settings(zioTestSettings)

lazy val ceesvee = project.in(file("."))
  .settings(commonSettings)
  .settings(noPublishSettings)
  .aggregate(
    core,
    benchmark,
  )

lazy val core = module("core")
  .settings(
    libraryDependencies ++= (CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(
        "com.chuusai" %% "shapeless" % shapelessVersion,
      )
      case _ => Seq.empty
    }),
  )

lazy val benchmark = proj("benchmark", None)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "com.univocity" % "univocity-parsers" % "2.9.1",
      "com.github.tototoshi" %% "scala-csv" % "1.3.10",
    ),
  )
  .dependsOn(core)
  .enablePlugins(JmhPlugin)
