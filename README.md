sbt-robovm
==========

sbt-robovm is an extension for the Scala build tool that aims to make it as simple as possible to get started with Scala on iOS using [RoboVM](http://www.robovm.org/) (version 0.0.5)

## Setup

Edit your plugins.sbt to include:

    resolvers += Resolver.url("scalasbt snapshots", new URL("http://repo.scala-sbt.org/scalasbt/sbt-plugin-snapshots"))(Resolver.ivyStylePatterns)

    addSbtPlugin("com.hagerbot" % "sbt-robovm" % "0.1.0-SNAPSHOT")

And then create an sbt project using RobovmProject.

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