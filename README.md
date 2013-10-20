sbt-robovm
==========

sbt-robovm is an extension for the Scala build tool that aims to make it as simple as possible to get started with Scala on iOS using [RoboVM](http://www.robovm.org/) (version 0.0.5)

## Setup

1. Get a computer running Mac OS X

2. Install Xcode (tested with 4.6.3 and 5.0)

3. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)

4. Download and extract [robovm-0.0.5.tar.gz](http://download.robovm.org/robovm-0.0.5.tar.gz) to one of these places:
 * $ROBOVM_HOME
 * ~/Applications/robovm/
 * ~/.robovm/home/
 * /usr/local/lib/robovm/
 * /opt/robovm/
 * /usr/lib/robovm/

5. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)

6. See [scala-ios-demos](http://github.com/ajhager/scala-ios-demos) for an example of setting up a new project

## Usage

If you've setup your 'ios' project to use RobovmProject:

    $ sbt ios/device
    $ sbt ios/iphone-sim
    $ sbt ios/ipad-sim
    $ sbt ios/ipa

## Notes

The first time you try to compile a program, RoboVM must compile the Java and Scala standard libraries. This can take a few minutes, but the output of this process is cached. Subsequent compilations will be much faster.

If you are having issues after install Xcode 5, run the following command and agree to Apple's new license:

    xcrun -sdk iphoneos -f clang

## Hacking on the plugin

If you need to make modifications to the plugin itself, you can compile and install it locally:

    $ git clone git://github.com/ajhager/sbt-robovm.git
    $ cd sbt-robovm
    $ sbt publish-local

## Projects using the plugin

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8)
