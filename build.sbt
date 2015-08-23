import sbt._
import Keys._
import bintray._

val roboVersion = "1.6.1-SNAPSHOT"

lazy val sbtRoboVM = (project in file(".")).
  enablePlugins(BuildInfoPlugin).
  settings(
    name := "sbt-robovm",
    licenses += ("BSD 3-Clause", url("http://opensource.org/licenses/BSD-3-Clause")),
    organization := "org.roboscala",
    version := roboVersion,
    sbtPlugin := true,
    publishMavenStyle := false,
    bintrayOrganization := None,
    bintrayRepository := "sbt-plugins",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-Xfatal-warnings"),
    javacOptions ++= Seq("-source","6","-target","6"),
    resolvers ++= {if (roboVersion.contains("-SNAPSHOT")) Seq(Resolver.sonatypeRepo("snapshots")) else Seq()},
    libraryDependencies += "org.robovm" % "robovm-dist-compiler" % roboVersion,
    libraryDependencies += "org.robovm" % "robovm-maven-resolver" % roboVersion,
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "sbtrobovm"
  )
