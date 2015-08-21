sbt-robovm
==========

sbt-robovm is a plugin for the Scala build tool that aims to make it as simple as possible to compile Scala (and Java) code to binaries for iOS, linux, and OSX using [RoboVM](http://www.robovm.org/)

## Setup

1. Install [Xcode 6](https://itunes.apple.com/us/app/xcode/id497799835)
1. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
1. See [roboscala-samples](https://github.com/roboscala/roboscala-samples) for example on how to use and configure

## Usage

First, add the plugin into your project by adding `addSbtPlugin("org.roboscala" % "sbt-robovm" % "1.6.0")`
into `project/plugins.sbt`. file. _(The file name (not extension) may actually be different, but such is the convention)_
This plugins version is in sync with RoboVM version, so it is always clear which RoboVM you end up using.

### Project Creation

To use the plugin, you have to create a [.scala build file](http://www.scala-sbt.org/0.13/tutorial/Full-Def.html)
and define what kind of project you are creating.
When building an iOS app (you probably are), define the project as follows _(example `project/MyBuild.scala` file)_:

```scala
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._

object MyBuild extends Build {
	lazy val root = iOSProject(id = "myproject", base = file("."), settings = Seq(
		/* Your settings, see below. */
	))
}
```

If you are, on the other hand, creating a native console application, use `NativeProject` instead of `iOSProject`.

### Tasks

There are different tasks defined for iOS and native console projects.

#### iOS Tasks

* `iphoneSim` and `ipadSim`
	* Runs the application in iPhone and iPad simulator, respectively
* `simulator`
	* Runs the application on the simulator specified by `robovmSimulatorDevice` setting
* `device`
	* Runs the app on the connected device
	* If you are working with multiple devices, there are two mechanisms at play: it is possible to specify the order of preference of devices using `preferredDevices` task. When not specified, plugin will attempt to connect to last device it was connected to.
* `ipa`
	* Creates the .ipa archive for upload to the App Store or other distribution
	* Both 32 and 64 bit slices are created automatically
* `simulatorDevices`
	* Prints all installed simulator devices

#### Native Tasks

* `native`
	* Runs the native console application
	* Connecting the input to interactive apps is not implemented. Recommended workaround is to execute compiled binary (in target/robovm/) in separate Terminal window.
* `nativeBuild`
	* Same as above, but does not execute the binary, only builds the app
	
#### Shared Tasks

* `robovmLicense`
	* Allows you to enter your [RoboVM license key](http://robovm.com/pricing/) to get access to premium features, such as line numbers in stack traces and debugger support _(interface builder integration is not yet implemented in this plugin)_

### Settings

As with tasks, there are some settings, that are only meaningful in iOS projects.
Some settings are actually implemented as tasks.

#### Shared

* `robovmConfiguration` _Either[File,Elem]_
	* The most important key, specifies the configuration of your app, the [**robovm.xml**](http://docs.robovm.com/configuration.html) file
	* If you have a real file, set it to `Left(file("path-to-your/robovm.xml"))`
	* If you want to specify it in-place, use the built-in scala support for XML literals: `Right(<config> ... </config>)`
	* See examples in the [sample repository](https://github.com/roboscala/roboscala-samples/blob/master/project/SampleBuild.scala)
* `robovmProperties` _Either[File, Map[String, String]]_
	* [robovm.properties](http://docs.robovm.com/configuration.html) file contains key-value pairs substituted into robovm.xml and Info.plist
	* If you have a real file with these, set this to `Left(file("path-to-your-file"))`
	* If you wish to generate these in your build script: `Right(Map("some-key" -> "some-value", ...))`
	* By default, this contains set of useful values:
		* `app.name` - Value of `name` sbt key
		* `app.executable` - Name of executable derived from `name` sbt key
		* `app.mainclass` - Fully specified main class of your application, either detected or specified in `mainClass` key
* `robovmTarget64bit` _Boolean_
	* Whether to build 64bit executables for the device
	* Default is **false** therefore you will need to set it to `true` if your device is newer and has a 64bit processor
* `robovmHome` _Config.Home_
	* Return the home of RoboVM installation.
	* By default, this task downloads RoboVM distribution into a local maven repository and unpacks it there, so there is no need to touch this unless you have a good reason to.
* `robovmInputJars` _Seq[File]_
	* Jars and classes to feed into the RoboVM compiler
	* By default, this returns `fullClasspath`, which is in most cases correct. You may want to override this if you want to modify the compiled classes first somehow (for example when using [ProGuard](http://proguard.sourceforge.net)).
* `robovmVerbose` _Boolean_
	* Setting this to true will propagate RoboVM debug-level messages to info-level
	* Useful when debugging RoboVM or plugin, otherwise not so much
* `robovmDebug` _Boolean_
	* Whether to enable RoboVM debugger
	* See _Debugging_ section below first
	* Needs commercial license, run `robovmLicense` task to enter yours
	* Port can be specified with `robovmDebugPort`
* `robovmDebugPort` _Int_
	* Port on which RoboVM debugger will listen (when enabled, see `robovmDebug`)


#### iOS Only

* `provisioningProfile` _Option[String]_
	* Specify provisioning profile to use when signing iOS code
	* Profile can be specified by name, UUID, app prefix, etc.
	* See _Tips_ section
* `signingIdentity` _Option[String]_
	* Specify signing identity to use when signing iOS code
	* Signing identity can be specified by name, fingerprint, etc.
	* See _Tips_ section
* `robovmSimulatorDevice` _Option[String]_
	* Name of device to be used in `simulator` task
	* Use `simulatorDevices` task to list all installed devices
* `skipSigning` _Option[Boolean]_
	* Setting this to `Some(true/false)` overrides default signing behavior and allows you to test without proper certificates and identities
* `preferredDevices` _Seq[String]_
	* List of iOS device ID's listed in the priority in which you want to connect to them if multiple devices are connected
	
### Debugging

Line numbers will be enabled automatically when the license is entered (see `robovmLicense` task).

To use the RoboVM debugger, prefix your task invocations with `debug:` (example: `$ sbt myproject/debug:ipadSim`).
This sets the scope to the `Debug` configuration, in which the debugger is enabled and the debug port is set, by default to 5005.
Running with the debugger enabled will allow you to connect to a running application with a java debugger.

### Tips

* All paths in configuration are relative to the base directory (one up from your Build.scala)
* During typical development, one usually ends up with two pairs of signing identity and profile, one for development and one for deployment. It is possible to scope the `signingIdentity/Profile` keys to automatically use the deployment pair when building an ipa:
```scala
provisioningProfile := Some("name of development profile"),
signingIdentity := Some("name of development identity"),
provisioningProfile in ipa := Some("name of distribution profile"),
signingIdentity in ipa := Some("name of distribution identity")
```
* You can download simulators for more iOS versions in Xcode. (Xcode includes only the latest iOS simulator by default)
* The first time you try to compile a program, RoboVM must compile the Java and Scala standard libraries. This can take a few minutes, but the output of this process is cached. Subsequent compilations will be much faster.
* If you are having issues after installing Xcode, open Xcode and agree to the license or open a Terminal and run xcrun.

## Hacking on the plugin

If you need to make modifications to the plugin itself, you can compile and install it locally:

```bash
$ git clone git://github.com/roboscala/sbt-robovm.git
$ cd sbt-robovm
$ sbt publish-local
```

### Contributing

Reporting any issues you encounter helps. If you want to help improving the plugin, feel free to make a PR.

## Projects using the plugin

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8) _(Uses older version)_
