sbt-robovm
==========

sbt-robovm is a plugin for the Scala build tool that aims to make it as simple as possible to compile Scala (and Java) code to binaries for iOS, linux, and OSX using [RoboVM](http://www.robovm.org/) (version 1.0.0-alpha-04)

*NOTE* Due to a bug in LLVM, most samples will fail to build for the simulator unless run through ProGuard first. The sample projects show how to use set that up, but your builds will take _much_ longer. Testing on an actual device is recommended until the bug is fixed.

## Changelog

* Changed the organization from `com.hagerbot` to `org.roboscala`

## Setup

1. Install Xcode 6.x
1. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
1. Download and extract [robovm-1.0.0-alpha-04.tar.gz](http://download.robovm.org/robovm-1.0.0-alpha-04.tar.gz) to one of these places:
	* ~/Applications/robovm/
	* ~/.robovm/home/
	* /usr/local/lib/robovm/
	* /opt/robovm/
	* /usr/lib/robovm/
1. See [roboscala-samples](http://github.com/roboscala/roboscala-samples) for project creation and configuration

## Usage

```bash
$ sbt ios/device
$ sbt ios/iphone-sim
$ sbt ios/ipad-sim
$ sbt ios/ipa
$ sbt ios/simulator
```

### Simulators

The `simulator` task will run the program on device specified by the `simulatorDevice` setting key. For example: `RobovmPlugin.simulatorDevice := Some("iPad-Air, 7.1")`

To see all installed simulators, run `$ sbt simulator-devices`.

You can download simulators for more iOS versions in Xcode. (Xcode includes only the latest iOS simulator by default.)

### Native

If you are using the plugin to build a native desktop project:

```bash
$ sbt project-name/native
```


## Notes

The first time you try to compile a program, RoboVM must compile the Java and Scala standard libraries. This can take a few minutes, but the output of this process is cached. Subsequent compilations will be much faster.

If you are having issues after installing Xcode, open Xcode and agree to the license or open a Terminal and run xcrun.

## Hacking on the plugin

If you need to make modifications to the plugin itself, you can compile and install it locally:

```bash
$ git clone git://github.com/roboscala/sbt-robovm.git
$ cd sbt-robovm
$ sbt publish-local
```

## Projects using the plugin

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8)
