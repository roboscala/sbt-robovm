sbt-robovm
==========

sbt-robovm is a plugin for the Scala build tool that aims to make it as simple as possible to compile Scala (and Java) code to binaries for iOS, linux, and OSX using [RoboVM](http://www.robovm.org/) (version 1.2.0)

## Changes from cleanup:
* There are two base "projects", iOSProject and NativeProject. Because iOS app cannot be directly compiled as
a native (console) application, it does not make sense to provide settings and tasks of opposite projects to them.
It seems that this was attempted at some point during original development, but never completed.
* Removed settings that can be set in robovm.xml, because we support inline xml now, leveraging Scala's xml syntax.
This allows access to much more settings with better flexibility. But files can still stay external. (This could have
been done to iOS plists as well, but those are bigger and it would not bring much benefits.)

## How to use?
If you want to try this before (if!) it gets to maven, you'll have to download and compile it yourself.
But it's easy, see below for "Hacking on the plugin" section, just instead of
`git clone git://github.com/roboscala/sbt-robovm.git` use `git clone https://github.com/Darkyenus/sbt-robovm.git`.

## How to migrate?
If you are already using older version (that on master branch or original repository) there are a few things you'll have to change,
since there has been some breaking changes.

1.  There is no longer any RobovmProject, use NativeProject or iOSProject instead. Usage remains same.
2.  Many setting keys for settings that could be set inside robovm.xml no longer exist. Those include `frameworks`, `robovmResources`, `skipPngCrush`, `iosInfoPlist` etc.
Set those settings directly in [robovm.xml](http://docs.robovm.com/configuration.html).
3.  robovm.xml and robovm.properties can be set in code. You can have robovm.xml as file and pass it changing stuff through `robovmProperties` or put robovm.xml directly in sbt code through `robovmConfiguration` key.

## Setup

1. Install [Xcode 6](https://itunes.apple.com/us/app/xcode/id497799835)
1. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
1. See [roboscala-samples](https://github.com/Darkyenus/roboscala-samples) for project creation and configuration

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

### Native

If you are using the plugin to build a native desktop project:

```bash
$ sbt project-name/native
```


## Notes

The first time you try to compile a program, RoboVM must compile the Java and Scala standard libraries. This can take a few minutes, but the output of this process is cached. Subsequent compilations will be much faster.

If you are having issues after installing Xcode, open Xcode and agree to the license or open a Terminal and run xcrun.

Plugin will by default download and unpack RoboVM by itself to local maven repository.
If you wish to override that, download and extract [robovm-1.3.0.tar.gz](http://download.robovm.org/robovm-1.3.0.tar.gz) anywhere you wish.
Then add `distHome := Some(file("PATH_TO_EXTRACTED_ROBOVM"))` to your build settings.
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

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8) _(Uses older version)_
