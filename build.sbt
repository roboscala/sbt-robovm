sbtPlugin := true

version := "0.1.0"

name := "sbt-robovm"

organization := "com.hagerbot"

resolvers += "Maven Central Server" at "http://repo1.maven.org/maven2"

libraryDependencies += "org.robovm" % "robovm-compiler" % "0.0.4"
