name := "sbt-robovm"

organization := "com.hagerbot"

version := "0.1.0-SNAPSHOT"

scalacOptions ++= Seq("-unchecked", "-deprecation", "-Xcheckinit", "-Xfatal-warnings")

publishMavenStyle := false

publishTo <<= (version) { version: String =>
  val scalasbt = "http://scalasbt.artifactoryonline.com/scalasbt/"
  val (name, url) = if (version.contains("-"))
    ("sbt-plugin-snapshots", scalasbt+"sbt-plugin-snapshots")
  else
    ("sbt-plugin-releases", scalasbt+"sbt-plugin-releases")
  Some(Resolver.url(name, new URL(url))(Resolver.ivyStylePatterns))
}

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

libraryDependencies += "org.robovm" % "robovm-compiler" % "0.0.4"

sbtPlugin := true

