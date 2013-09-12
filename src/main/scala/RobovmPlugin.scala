package sbtrobovm

import sbt._
import Keys._

object RobovmPlugin extends Plugin {
  val executableName = SettingKey[String]("executable-name")
  val frameworks = SettingKey[Seq[String]]("frameworks")
  val nativePath = SettingKey[File]("native-path")

  val device = TaskKey[Unit]("device", "Start package on device after installation")
  val iphoneSim = TaskKey[Unit]("iphone-sim", "Start package on iphone simulator")
  val ipadSim = TaskKey[Unit]("ipad-sim", "Start package on ipad simulator")
  val ipa = TaskKey[Unit]("ipa", "Create an ipa file for the app store")

  val RobovmProject = RobovmProjects.Standard
}
