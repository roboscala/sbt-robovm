sbt-robovm
==========

sbt-robovm is an extension for the Scala build tool sbt which aims to make it as simple as possible to get started with Scala on iOS.

## Basic usage

If you've setup your 'ios' project to use RobovmProject:

   > ios/device

   > ios/iphone-sim

   > ios/ipad-sim

## Hacking on the plugin

If you need make modifications to the plugin itself, you can compile
and install it locally:

    $ git clone git://github.com/ajhager/sbt-robovm.git
    $ cd sbt-robovm
    $ sbt publish-local

## Projects using the plugin

[libgdx-sbt-project.g8](https://github.com/ajhager/libgdx-sbt-project.g8)
