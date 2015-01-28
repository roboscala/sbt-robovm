package sbtrobovm

import sbt._

object RobovmPlugin extends Plugin {
  val RoboVMVersion = "1.0.0-beta-03"

  /* General Settings and Setting tasks */
  val distHome = TaskKey[File]("dist-home","Return the home of RoboVM installation. Will download to local maven repository by default.")
  val robovmInputJars = TaskKey[Seq[File]]("robovm-input-jars","Jars fed into RoboVM compiler. fullClasspath in compile by default.")
  val robovmVerbose = SettingKey[Boolean]("robovm-verbose","Propagates robovm Debug messages to Info level, to be visible")
  val simulatorDevice = SettingKey[Option[String]]("simulator-device","Simulator device to be used in simulator task")
  val robovmProperties = SettingKey[Option[Either[File, Map[String, String]]]]("robovm-properties","Values that might be used in config-file substitutions")
  val configFile = SettingKey[Option[File]]("config-file","Path to xml with robovm configuration")
  val skipSigning = SettingKey[Option[Boolean]]("skip-signing","Whether to override signing behavior")

  /* Tasks */
  // iOS Only
  val simulator = TaskKey[Unit]("simulator","Start package on specified device")
  val device = TaskKey[Unit]("device", "Start package on device after installation")
  val iphoneSim = TaskKey[Unit]("iphone-sim", "Start package on iphone simulator")
  val ipadSim = TaskKey[Unit]("ipad-sim", "Start package on ipad simulator")
  val ipa = TaskKey[Unit]("ipa", "Create an ipa file for the app store")
  // Native Only
  val native = TaskKey[Unit]("native", "Run as native console application")

  /* Tools */
  val robovmLicense = TaskKey[Unit]("robovm-license","Launch UI for entering a RoboVM license key.")
  // iOS Only
  val simulatorDevices = TaskKey[Unit]("simulator-devices", "Prints all available simulator devices to be used in simulator-device-name setting")

  /* Things that should be in robovm.xml */
  val executableName = SettingKey[String]("executable-name","Name of produced executable.")
  val forceLinkClasses = SettingKey[Seq[String]]("force-link-classes")
  val frameworks = SettingKey[Seq[String]]("frameworks","Frameworks that program uses. iOS and OSX only.")
  val nativePath = SettingKey[Seq[File]]("native-path")
  val robovmResources = TaskKey[Seq[File]]("robovm-resources")
  val skipPngCrush = SettingKey[Boolean]("skip-png-crush")
  val flattenResources = SettingKey[Boolean]("flatten-resources")
  // iOS Only
  val iosSdkVersion = SettingKey[Option[String]]("ios-sdk-version")
  val iosSignIdentity = SettingKey[Option[String]]("ios-sign-identity")
  val iosProvisioningProfile = SettingKey[Option[String]]("ios-provisioning-profile")
  val iosInfoPlist = SettingKey[Option[File]]("ios-info-plist")
  val iosEntitlementsPlist = SettingKey[Option[File]]("ios-entitlements-plist")
  val iosResourceRulesPlist = SettingKey[Option[File]]("ios-resource-rules-plist")

  val iOSProject = RobovmProjects.iOSProject
  val NativeProject = RobovmProjects.NativeProject
}
