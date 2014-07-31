name := "sbt-robovm"

organization := "com.hagerbot"

version := "0.0.14-SNAPSHOT"

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

libraryDependencies += "org.robovm" % "robovm-compiler" % version.value.replace("-SNAPSHOT","")

libraryDependencies += "org.robovm" % "robovm-rt" % version.value.replace("-SNAPSHOT","")

libraryDependencies += "org.robovm" % "robovm-objc" % version.value.replace("-SNAPSHOT","")

libraryDependencies += "org.robovm" % "robovm-cocoatouch" % version.value.replace("-SNAPSHOT","")

libraryDependencies += "org.robovm" % "robovm-cacerts-full" % version.value.replace("-SNAPSHOT","")

sbtPlugin := true
