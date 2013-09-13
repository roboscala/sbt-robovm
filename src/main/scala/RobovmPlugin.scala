package sbtrobovm

import sbt._
import Keys._

object RobovmPlugin extends Plugin {
  val executableName = SettingKey[String]("executable-name")
  val forceLinkClasses = SettingKey[Seq[String]]("force-link-classes")
  val frameworks = SettingKey[Seq[String]]("frameworks")
  val nativePath = SettingKey[File]("native-path")
  val distHome = SettingKey[Option[File]]("dist-home")
  val skipPngCrush = SettingKey[Boolean]("skip-png-crush")
  val flattenResources = SettingKey[Boolean]("flatten-resources")
  val propertiesFile = SettingKey[Option[File]]("properties-file")
  val configFile = SettingKey[Option[File]]("config-file")

  val iosSdkVersion = SettingKey[Option[String]]("ios-sdk-version")
  val iosSignIdentity = SettingKey[Option[String]]("ios-sign-identity")
  val iosInfoPlist = SettingKey[Option[File]]("ios-info-plist")
  val iosEntitlementsPlist = SettingKey[Option[File]]("ios-entitlements-plist")
  val iosResourceRulesPlist = SettingKey[Option[File]]("ios-resource-rules-plist")

  val device = TaskKey[Unit]("device", "Start package on device after installation")
  val iphoneSim = TaskKey[Unit]("iphone-sim", "Start package on iphone simulator")
  val ipadSim = TaskKey[Unit]("ipad-sim", "Start package on ipad simulator")
  val ipa = TaskKey[Unit]("ipa", "Create an ipa file for the app store")

  val RobovmProject = RobovmProjects.Standard
}
