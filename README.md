sbt-robovm
==========

sbt-robovm is a plugin for the Scala build tool that aims to make it as simple as possible to compile Scala (and Java) code to binaries for iOS, linux, and OSX using [RoboVM](http://www.robovm.org/) (version 1.6.0)

## Setup

1. Install [Xcode 6](https://itunes.apple.com/us/app/xcode/id497799835)
1. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
1. See [roboscala-samples](https://github.com/Darkyenus/roboscala-samples) for project creation and configuration

## Usage

```bash
$ sbt project-name/device
$ sbt project-name/iphoneSim
$ sbt project-name/ipadSim
$ sbt project-name/ipa
$ sbt project-name/simulator
$ sbt project-name/native
```

### Simulators

The `simulator` task will run the program on device specified by the `simulatorDevice` setting key. For example: `RobovmPlugin.simulatorDevice := Some("iPad-Air, 7.1")`

To see all installed simulators, run `$ sbt simulator-devices`.

You can download simulators for more iOS versions in Xcode. (Xcode includes only the latest iOS simulator by default.)

## Installing RoboVM

This plugin will by default download and unpack RoboVM by itself to local maven repository.
If you wish to override that, download and extract [robovm-1.6.0.tar.gz](http://download.robovm.org/robovm-1.6.0.tar.gz) anywhere you wish.
Then add `distHome := Some(file("PATH_TO_EXTRACTED_ROBOVM"))` to your build settings.

Alternatively, extract RoboVM to one of these places:
* ~/Applications/robovm/
* ~/.robovm/home/
* /usr/local/lib/robovm/
* /opt/robovm/
* /usr/lib/robovm/
And to your settings add `distHome := Some(null)`.

### Signing details

When you develop, you will most likely want to specify signing identity and provisioning profile.
To do that, add following keys:

```
provisioningProfile := Some("<Name of your development provisioning profile>"),
signingIdentity := Some("<Name of your development signing identity>"),
provisioningProfile in ipa := Some("<Name of your distribution provisioning profile>"),
signingIdentity in ipa := Some("<Name of your distribution signing identity>")
```

You can also specify other identifying features instead of name, for example fingerprint.

Specifying profile or identity that isn't installed, will print information about those which are installed.

### Native libraries

If you are using libraries, which need platform native libs, you have to specify them in [robovm.xml <libs>](http://docs.robovm.com/configuration.html).
They will however be often packed in a versioned jar, downloaded from maven.
For example game framework [libGDX](http://www.libgdx.com/) does this.

In that case, you can either unpack natives manually, or use built in extractor.
Just add `ManagedNatives` configuration next to that library dependency:
```scala
libraryDependencies += "com.badlogicgames.gdx" % "gdx-platform" % gdxVersion % ManagedNatives classifier "natives-ios"
```
And then tell robovm.xml about it. If you have inlined xml in your build files, it's very easy,
just add `RobovmPlugin.iOSRobovmXMLManagedNatives.value` where you would normally put `<libs>...</libs>` tags.

If you have robovm.xml in file, add to your inlined properties something like:  
`"nativeLibraries" -> RobovmPlugin.iOSRobovmXMLManagedNatives.value.toString()`  
And in robovm.xml, instead of `<libs>` tags add `${nativelibs}`.

`iOSRobovmXMLManagedNatives` is a task, which detects all `libraryDependencies` configured to `ManagedNatives`,
extracts all iOS native libraries (files with `.a` extension) from its jars to a temporary location (in sbt's target directory)
and generates xml containing
```xml
<libs>
	<lib> absolute path to first extracted lib </lib>
	etc.
</libs>
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

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8) _(Uses older version)_
