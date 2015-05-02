name := "sbt-robovm"

organization := "org.roboscala"

val roboVersion = "1.1.0" //When changing, change also RoboVMVersion in sbtrobovm.RobovmPlugin

version := roboVersion + "-SNAPSHOT"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-Xfatal-warnings")

javacOptions ++= Seq("-source","6","-target","6")

publishMavenStyle := false

publishTo := {
  val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
  val (name, url) = if (version.value.contains("-SNAPSHOT"))
    ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
  else
    ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
  Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}

libraryDependencies += "org.robovm" % "robovm-dist-compiler" % roboVersion

libraryDependencies += "org.robovm" % "robovm-maven-resolver" % roboVersion

sbtPlugin := true
