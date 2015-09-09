# sbt-robovm

sbt-robovm is a plugin for the Scala build tool that aims to make it as simple as possible to compile Scala (and Java) code to binaries for iOS, linux, and OSX using [RoboVM](http://www.robovm.org/)

## Setup

1. Install [Xcode 6](https://itunes.apple.com/us/app/xcode/id497799835)
1. Install [JDK 7](http://www.oracle.com/technetwork/java/javase/downloads/jdk7-downloads-1880260.html)
1. Install [sbt](http://www.scala-sbt.org/release/docs/Getting-Started/Setup.html)
1. See [roboscala-samples](https://github.com/roboscala/roboscala-samples) for example on how to use and configure

## Add the Plugin

First, add the plugin to your project by appending `addSbtPlugin("org.roboscala" % "sbt-robovm" % "1.7.0")`
into the `project/plugins.sbt` file. _The file name (not extension) may actually be different, but such is the convention._
The plugin's version is in sync with the RoboVM version it uses, so it should always be clear which RoboVM is being used.

## Project Creation

All you have to do to use the plugin, is to add `iOSRoboVMSettings` key (or `nativeRoboVMSettings` if you are creating a native project)
to your [`build.sbt`](http://www.scala-sbt.org/0.13/tutorial/Basic-Def.html) file.

If you are creating a multi-project build, prepend that to your settings Seq:

```scala
lazy val myproject = Project(id = "myproject", base = file("myproject"), settings = iOSRoboVMSettings ++ Seq(
	/* More settings */
))
```

## Tasks

There are different tasks defined for iOS and native console projects.

### Shared

* `robovmLicense`
	* Allows you to enter your [RoboVM license key](http://robovm.com/pricing/) to get access to premium features, such as line numbers in stack traces, debugger support and interface builder integration.

### iOS

* `iphoneSim` and `ipadSim`
	* Build and run the app in a iPhone or iPad simulator, respectively.
* `simulator`
	* Build and run the app on a simulator specified by the `robovmSimulatorDevice` setting.
* `device`
	* Build and run the app on a connected device.
	* It is possible to specify the order of preference of devices using the `robovmPreferredDevices` task.
	* Otherwise, the plugin will attempt to connect to the last device it has used.
* `ipa`
	* Create the .ipa archive for upload to the App Store or other distribution.
* `simulatorDevices`
	* Print all installed simulator devices.

### Native

* `native`
	* Build and run a native console application.
	* Connecting the input to interactive apps is not implemented. Recommended workaround is to execute compiled binary (in target/robovm/) in separate Terminal window.
* `nativeBuild`
	* Same as `native`, but does not execute the binary.
	
## Settings

As with tasks, there are some settings that are only meaningful in iOS projects. Some settings are actually implemented as tasks.

### Shared

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

### iOS Only

* `robovmProvisioningProfile` _Option[String]_
	* Specify provisioning profile to use when signing iOS code
	* Profile can be specified by name, UUID, app prefix, etc.
	* See _Tips_ section
* `robovmSigningIdentity` _Option[String]_
	* Specify signing identity to use when signing iOS code
	* Signing identity can be specified by name, fingerprint, etc.
	* See _Tips_ section
* `robovmSimulatorDevice` _Option[String]_
	* Name of device to be used in `simulator` task
	* Use `simulatorDevices` task to list all installed devices
* `robovmSkipSigning` _Option[Boolean]_
	* Setting this to `Some(true/false)` overrides default signing behavior and allows you to test without proper certificates and identities
* `robovmPreferredDevices` _Seq[String]_
	* List of iOS device ID's listed in the priority in which you want to connect to them if multiple devices are connected
* `robovmIBScope` _Scope_
    * Scope in which `interfaceBuilder` command operates. Defaults to `ThisScope`.
    * Only reason to change this is if you have a custom configuration
	
## Debugging _(licensed only)_

Line numbers will be enabled automatically when the license is entered (see `robovmLicense` task).

To use the RoboVM debugger, prefix your task invocations with `debug:` (example: `$ sbt myproject/debug:ipadSim`).
This sets the scope to the `Debug` configuration, in which the debugger is enabled and the debug port is set, by default to 5005.
Running with the debugger enabled will allow you to connect to a running application with a java debugger.

### Using the debugger from IntelliJ IDEA 14

1. Create a new "Remote" Run/Debug configuration
    - Top bar, right next to the "Make" button -> "Edit configurations" -> "+" -> "Remote"
    - All settings can be left to their default values
1. Run the project in debug mode from SBT (for example `$ sbt debug:ipadSim`)
1. Make sure the configuration from step 1 is selected and press the "Debug" button
1. IntelliJ will connect to the running application and you can start debugging like you are used to with standard Java debugging

Application execution will pause before your `main` method and wait for the debugger to attach. Then it will continue normally.

## Interface Builder _(licensed only)_

This plugin offers a basic integration with XCode's [Interface Builder](https://developer.apple.com/xcode/interface-builder/).
There are some excellent tutorials on how to use IB with IntelliJ on [RoboVM website](http://docs.robovm.com/tutorials/ib-basics/ib-basics.html).
Getting familiar with them is recommended, since the workflow in sbt is similar.

In the core of this feature is an interactive `interfaceBuilder` _command_.
Run the command inside your iOS project, it will generate XCode project and open it in the Interface Builder.
Then it will watch your code sources and when any of them change, it will recompile the project and update the XCode project accordingly.
XCode will show new `IBOutlet`s and `IBAction`s very shortly after that.

You will also notice, that the prompt in the sbt console will change to "interfaceBuilder >".
That notes that you are in a special mode, where the `interfaceBuilder` command is still running, but you can still run
any commands/tasks as usual, so you can, for example, run the `ipadSimulator` task to quickly view your changes on device.
Pressing enter, without any command, will exit the `interfaceBuilder` mode and you will be back to standard sbt prompt.

_NOTE: TAB completion currently does not work in `interfaceBuilder` mode_

Because `interfaceBuilder` is a command and not a task (for technical reasons), it can not be scoped.
Therefore, doing something like `myProject/interfaceBuilder` will not work.
To work around this, use `project myProject` command first, to switch active project to that and then run `interfaceBuilder`.
If you need even more granular scoping, use the `robovmIBScope` setting.

### Tips

* All paths in the configuration are relative to the base directory.
* During typical development, you usually end up with two pairs of signing identity and profile, one for development and one for distribution. It is possible to scope the `robovmSigningIdentity/Profile` keys to automatically use the distribution pair when building an ipa:
```scala
robovmProvisioningProfile := Some("name of development profile"),
robovmSigningIdentity := Some("name of development identity"),
robovmProvisioningProfile in ipa := Some("name of distribution profile"),
robovmSigningIdentity in ipa := Some("name of distribution identity")
```
* You can download simulators for more iOS versions in Xcode. Xcode includes only the latest iOS simulator by default.
* The first time you try to compile a program, RoboVM must compile the Java and Scala standard libraries. This can take a few minutes, but the output of this process is cached. Subsequent compilations will be much faster.
* If you are having issues after installing Xcode, open Xcode and agree to the license or open a Terminal and run xcrun.

## Hacking on the plugin

If you need to make modifications to the plugin itself, you can compile and install it locally:

```bash
$ git clone git://github.com/roboscala/sbt-robovm.git
$ cd sbt-robovm
$ sbt +publish-local
```

Then in your project/plugins.sbt file:

```scala
// Relevant when testing with RoboVM snapshot build
resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.roboscala" % "sbt-robovm" % "1.6.1-SNAPSHOT")
```

When testing the changes, it may be useful to publish (locally) with different version than default,
to be sure that the changes really take place. To do that, int build.sbt change line:
```scala
    version := roboVMVersion.value,
```
to:
```scala
    version := roboVMVersion.value + "-YOUR_SUFFIX",
```

And the project/plugins.sbt of your project to:
```scala
// Relevant when testing with RoboVM snapshot build
resolvers += Resolver.sonatypeRepo("snapshots")

addSbtPlugin("org.roboscala" % "sbt-robovm" % "1.6.1-SNAPSHOT-YOUR_SUFFIX" changing())
```

### Contributing

Reporting any issues you encounter helps. If you want to help improving the plugin, feel free to make a PR.

## Projects using the plugin

[libgdx-sbt-project.g8](http://github.com/ajhager/libgdx-sbt-project.g8) _(Uses older version)_
