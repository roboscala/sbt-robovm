name := "sbt-robovm"

organization := "com.hagerbot"

val roboVersion = "0.0.14"

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

libraryDependencies ++= List("robovm-compiler", "robovm-rt", "robovm-objc", "robovm-cocoatouch", "robovm-cacerts-full").map("org.robovm" % _ % roboVersion)

sbtPlugin := true
