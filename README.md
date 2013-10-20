sbt-robovm
==========

sbt-robovm is an extension for the Scala build tool that aims to make it as simple as possible to get started with Scala on iOS using [RoboVM](http://www.robovm.org/) (version 0.0.5)

## Setup

1. Get a computer running Mac OS X
2. Install Xcode (tested with 4.6.3 and 5.0)
3. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
4. Download and extract [robovm-0.0.5.tar.gz](http://download.robovm.org/robovm-0.0.5.tar.gz) ($ROBOVM_HOME, ~/Applications/robovm/, ~/.robovm/home/, /usr/local/lib/robovm/, /opt/robovm/, or /usr/lib/robovm/)
5. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
6. Edit your plugins.sbt to include:

    resolvers += Resolver.url("scalasbt snapshots", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns)

    addSbtPlugin("com.hagerbot" % "sbt-robovm" % "0.1.0-SNAPSHOT")

7. Create an sbt project using RobovmProject:

    import sbt._
    import Keys._

    import sbtrobovm.RobovmPlugin._

    object ScaliOSBuild extends Build {
      lazy val hello = makeDemo("hello", "Hello Robo")

      def makeDemo(path: String, name: String, settings: Seq[Setting[_]] = Seq.empty): Project = {
        RobovmProject(path, file(path),
          settings = Defaults.defaultSettings ++ settings ++ Seq(
            scalaVersion := "2.10.3",
            executableName := name
          )
        )
      }
    }

## Basic usage

If you've setup your 'ios' project to use RobovmProject:

    $ sbt ios/device
    $ sbt ios/iphone-sim
    $ sbt ios/ipad-sim
    $ sbt ios/ipa

## Notes

The first time you try to compile a program, RoboVM must compile the Java and Scala standard libraries. This can take a few minutes, but the output of this process is cached. Subsequent compilations will be much faster.

## Hacking on the plugin

If you need to make modifications to the plugin itself, you can compile and install it locally:

    $ git clone git://github.com/ajhager/sbt-robovm.git
    $ cd sbt-robovm
    $ sbt publish-local

## Projects using the plugin

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8)
[scala-ios-demos](http://github.com/ajhager/scala-ios-demos)