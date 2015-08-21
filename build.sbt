import sbt._
import Keys._
import bintray._

val roboVersion = "1.6.0" //When changing, change also RoboVMVersion in sbtrobovm.RobovmPlugin

val sbtRoboVM = Project(
  id = "sbt-robovm",
  base = file("."),
  settings = Defaults.defaultSettings ++ Seq(
    licenses += ("BSD 3-Clause", url("http://opensource.org/licenses/BSD-3-Clause")),
    organization := "org.roboscala",
    version := roboVersion,
    sbtPlugin := true,
    publishMavenStyle := false,
    bintrayOrganization := None,
    bintrayRepository := "sbt-plugins",
    scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-Xfatal-warnings"),
    javacOptions ++= Seq("-source","6","-target","6"),
    libraryDependencies += "org.robovm" % "robovm-dist-compiler" % roboVersion,
    libraryDependencies += "org.robovm" % "robovm-maven-resolver" % roboVersion
  )
)
