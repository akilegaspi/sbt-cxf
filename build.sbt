enablePlugins(SbtPlugin)

name := "sbt-cxf"

homepage := Some(new URI("https://github.com/paymenthighway/sbt-cxf").toURL)
startYear := Some(2016)
licenses := Seq(("Apache 2", new URI("http://www.apache.org/licenses/LICENSE-2.0.txt").toURL))
organization := "io.paymenthighway.sbt"
organizationName := "Payment Highway Oy"
organizationHomepage := Some(url("https://paymenthighway.fi/en/"))

developers := List(
  Developer("margussipria", "Margus Sipria", "margus+sbt-cxf@sipria.fi", url("https://github.com/margussipria"))
)

scmInfo := Some(ScmInfo(
  browseUrl = url("http://github.com/paymenthighway/sbt-cxf"),
  connection = "scm:git:https://github.com/paymenthighway/sbt-cxf.git",
  devConnection = Some("scm:git:git@github.com:paymenthighway/sbt-cxf.git")
))

javacOptions ++= Seq("-source", "17", "-target", "17", "-Xlint")

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xfatal-warnings"
)

scalaVersion := "2.12.19"
