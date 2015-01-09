sbt-robovm
==========

sbt-robovm is a plugin for the Scala build tool that aims to make it as simple as possible to compile Scala (and Java) code to binaries for iOS, linux, and OSX using [RoboVM](http://www.robovm.org/) (version 1.0.0-beta-02)

## Changelog

* Changed the organization from `com.hagerbot` to `org.roboscala`
* Issue that prevented RoboVM from compiling certain Scala classes seems to be fixed now [(RoboVM issue #313)](https://github.com/robovm/robovm/issues/313)

## Setup

1. Install Xcode 6.x
1. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
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

Plugin will by default download and unpack RoboVM by itself to local maven repository.
If you wish to override that, download and extract [robovm-1.0.0-beta-02.tar.gz](http://download.robovm.org/robovm-1.0.0-beta-02.tar.gz) anywhere you wish.
Then add `distHome := Some(file("PATH_TO_ROBOVM_HOME"))` to your build settings.
Alternatively, extract RoboVM to one of these places:
* ~/Applications/robovm/
* ~/.robovm/home/
* /usr/local/lib/robovm/
* /opt/robovm/
* /usr/lib/robovm/

And to your settings add `distHome := Some(null)`.

## Hacking on the plugin

If you need to make modifications to the plugin itself, you can compile and install it locally:

```bash
$ git clone git://github.com/roboscala/sbt-robovm.git
$ cd sbt-robovm
$ sbt publish-local
```

## Projects using the plugin

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8)
