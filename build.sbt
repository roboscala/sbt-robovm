name := "sbt-robovm"

organization := "org.roboscala"

val roboVersion = "1.0.0-alpha-04"

version := roboVersion + "-SNAPSHOT"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-Xfatal-warnings")

publishMavenStyle := false

publishTo <<= (version) { version: String =>
  val scalasbt = "http://repo.scala-sbt.org/scalasbt/"
  val (name, url) = if (version.contains("-"))
    ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
  else
    ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
  Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}

libraryDependencies += "org.robovm" % "robovm-dist-compiler" % roboVersion

sbtPlugin := true
