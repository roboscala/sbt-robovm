package sbtrobovm

import org.robovm.compiler.config.Config
import sbt._

import scala.xml.Elem

object RobovmPlugin extends Plugin {
  val RoboVMVersion = "1.0.0-beta-04"

  /* General Settings and Setting tasks */
  val robovmHome = TaskKey[Config.Home]("robovmHome","Return the home of RoboVM installation. Will download to local maven repository by default.")
  val robovmInputJars = TaskKey[Seq[File]]("robovmInputJars","Jars fed into RoboVM compiler. fullClasspath in compile by default.")
  val robovmVerbose = SettingKey[Boolean]("robovmVerbose","Propagates robovm Debug messages to Info level, to be visible")
  val simulatorDevice = SettingKey[Option[String]]("simulatorDevice","Simulator device to be used in simulator task")
  val robovmProperties = TaskKey[Either[File, Map[String, String]]]("robovmProperties","Values that might be used in config-file substitutions")
  val robovmConfiguration = TaskKey[Either[File,Elem]]("robovmConfiguration","robovm.xml configuration")
  val skipSigning = SettingKey[Option[Boolean]]("skip-signing","Whether to override signing behavior") //Here because cannot be changed anywhere else

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

  val iOSProject = RobovmProjects.iOSProject
  val NativeProject = RobovmProjects.NativeProject
}
