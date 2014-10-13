package sbtrobovm

import sbt._

object RobovmPlugin extends Plugin {
  val executableName = SettingKey[String]("executable-name")
  val forceLinkClasses = SettingKey[Seq[String]]("force-link-classes")
  val frameworks = SettingKey[Seq[String]]("frameworks")
  val nativePath = SettingKey[Seq[File]]("native-path")
  val distHome = SettingKey[Option[File]]("dist-home")
  val robovmResources = TaskKey[Seq[File]]("robovm-resources")
  val skipPngCrush = SettingKey[Boolean]("skip-png-crush")
  val flattenResources = SettingKey[Boolean]("flatten-resources")
  val robovmProperties = SettingKey[Option[Either[File, Map[String, String]]]]("robovm-properties","Values that might be used in config-file substitutions")
  val configFile = SettingKey[Option[File]]("config-file","Path to xml with robovm configuration")
  val skipSigning = SettingKey[Option[Boolean]]("skip-signing","Whether to override signing behavior")

  val alternativeInputJars = TaskKey[Option[Seq[File]]]("alternative-input-jars","Jars used instead of fullClasspath when Some")

  val iosSdkVersion = SettingKey[Option[String]]("ios-sdk-version")
  val iosSignIdentity = SettingKey[Option[String]]("ios-sign-identity")
  val iosProvisioningProfile = SettingKey[Option[String]]("ios-provisioning-profile")
  val iosInfoPlist = SettingKey[Option[File]]("ios-info-plist")
  val iosEntitlementsPlist = SettingKey[Option[File]]("ios-entitlements-plist")
  val iosResourceRulesPlist = SettingKey[Option[File]]("ios-resource-rules-plist")

  val simulatorDevice = SettingKey[Option[String]]("simulator-device","Simulator device to be used in simulator-device task")
  val simulator = TaskKey[Unit]("simulator","Start package on specified device")
  val device = TaskKey[Unit]("device", "Start package on device after installation")
  val iphoneSim = TaskKey[Unit]("iphone-sim", "Start package on iphone simulator")
  val ipadSim = TaskKey[Unit]("ipad-sim", "Start package on ipad simulator")
  val ipa = TaskKey[Unit]("ipa", "Create an ipa file for the app store")

  val native = TaskKey[Unit]("native", "Run as native console application")

  val robovmDebug = SettingKey[Boolean]("robovm-debug","Propagates robovm Debug messages to Info level, to be visible")
  val simulatorDevices = TaskKey[Unit]("simulator-devices", "Prints all available simulator devices to be used in simulator-device-name setting")

  val RobovmProject = RobovmProjects.Standard
}
