package sbtrobovm

import org.robovm.compiler.config.Config
import sbt._

import scala.xml.Elem

object RobovmPlugin extends AutoPlugin with RobovmUtils {
  val RoboVMVersion:String = BuildInfo.roboVMVersion

  /* General settings and tasks */
  val robovmHome = taskKey[Config.Home]("Return the home of RoboVM installation. Will download to local maven repository by default.")
  val robovmInputJars = taskKey[Seq[File]]("Jars fed into RoboVM compiler. fullClasspath in compile by default.")
  val robovmVerbose = settingKey[Boolean]("Propagates robovm Debug messages to Info level, to be visible")
  val robovmSimulatorDevice = settingKey[Option[String]]("Simulator device to be used in simulator task")
  val robovmProperties = taskKey[Either[File, Map[String, String]]]("Values that might be used in config-file substitutions")
  val robovmConfiguration = taskKey[Either[File,Elem]]("robovm.xml configuration")
  val robovmDebugPort = settingKey[Int]("Port on which debugger will listen (when enabled)")
  val robovmDebug = settingKey[Boolean]("Whether to enable robovm debugger (Needs commercial license, run robovmLicense task to enter one)")
  val robovmTarget64bit = settingKey[Boolean]("Whether to build 64bit executables")

  /* Specific settings and tasks */
  // iOS Only
  val simulator = taskKey[Unit]("Start package on specified device")
  val device = taskKey[Unit]("Start package on device after installation")
  val iphoneSim = taskKey[Unit]("Start package on iphone simulator")
  val ipadSim = taskKey[Unit]("Start package on ipad simulator")
  val ipa = taskKey[Unit]("Create an ipa file for the app store")

  val robovmSkipSigning = settingKey[Option[Boolean]]("Whether to override signing behavior")
  val robovmProvisioningProfile = settingKey[Option[String]]("Specify provisioning profile to use when signing iOS code.")
  val robovmSigningIdentity = settingKey[Option[String]]("Specify signing identity to use when signing iOS code.")
  val robovmPreferredDevices = settingKey[Seq[String]]("List of iOS device ID's from which device will be chosen if multiple are detected.")
  // Native Only
  val native = taskKey[Unit]("Run as native console application")
  val nativeBuild = taskKey[Unit]("Compile and archive for distribution as native application")

  /* Tools */
  val robovmLicense = taskKey[Unit]("Launch UI for entering a RoboVM license key.")
  // iOS Only
  val simulatorDevices = taskKey[Unit]("Prints all available simulator devices to be used in simulator-device-name setting")

  object autoImport {
    val RoboVMVersion = RobovmPlugin.RoboVMVersion

    val robovmHome = RobovmPlugin.robovmHome
    val robovmInputJars = RobovmPlugin.robovmInputJars
    val robovmVerbose = RobovmPlugin.robovmVerbose
    val robovmSimulatorDevice = RobovmPlugin.robovmSimulatorDevice
    val robovmProperties = RobovmPlugin.robovmProperties
    val robovmConfiguration = RobovmPlugin.robovmConfiguration
    val robovmDebugPort = RobovmPlugin.robovmDebugPort
    val robovmDebug = RobovmPlugin.robovmDebug
    val robovmTarget64bit = RobovmPlugin.robovmTarget64bit

    /* Specific settings and tasks */
    // iOS Only
    val simulator = RobovmPlugin.simulator
    val device = RobovmPlugin.device
    val iphoneSim = RobovmPlugin.iphoneSim
    val ipadSim = RobovmPlugin.ipadSim
    val ipa = RobovmPlugin.ipa

    val robovmSkipSigning = RobovmPlugin.robovmSkipSigning
    val robovmProvisioningProfile = RobovmPlugin.robovmProvisioningProfile
    val robovmSigningIdentity = RobovmPlugin.robovmSigningIdentity
    val robovmPreferredDevices = RobovmPlugin.robovmPreferredDevices
    // Native Only
    val native = RobovmPlugin.native
    val nativeBuild = RobovmPlugin.nativeBuild

    /* Tools */
    val robovmLicense = RobovmPlugin.robovmLicense
    // iOS Only
    val simulatorDevices = RobovmPlugin.simulatorDevices

    //Settings
    val iOSRoboVMSettings = RobovmProjects.baseSettings ++ RobovmProjects.iOSProjectSettings
    val nativeRoboVMSettings = RobovmProjects.baseSettings ++ RobovmProjects.nativeProjectSettings
  }

  //This makes sure that settings below are applied
  override def trigger: PluginTrigger = allRequirements

  //This adds project-independent tools into the settings
  override def globalSettings: Seq[Def.Setting[_]] = RobovmProjects.toolSettings

  // Backwards compatibility
  @deprecated(message = "Use robovmProvisioningProfile instead", since = "1.7.0")
  val provisioningProfile = robovmProvisioningProfile
  @deprecated(message = "Use robovmSigningIdentity instead", since = "1.7.0")
  val signingIdentity = robovmSigningIdentity

  import sbt.Defaults._
  @deprecated(message = "Create project normally and prepend RobovmPlugin.autoImport.iOSRoboVMSettings to settings instead", since = "1.7.0")
  def iOSProject(
             id: String,
             base: File,
             aggregate: => Seq[ProjectReference] = Nil,
             dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
             delegates: => Seq[ProjectReference] = Nil,
             settings: => Seq[Def.Setting[_]] = Seq.empty,
             configurations: Seq[Configuration] = Configurations.default
             ) = Project(
    id,
    base,
    aggregate,
    dependencies,
    delegates,
    coreDefaultSettings ++ autoImport.iOSRoboVMSettings ++ settings,
    configurations
  )

  @deprecated(message = "Create project normally and prepend RobovmPlugin.autoImport.nativeRoboVMSettings to settings instead", since = "1.7.0")
  def NativeProject(
                  id: String,
                  base: File,
                  aggregate: => Seq[ProjectReference] = Nil,
                  dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
                  delegates: => Seq[ProjectReference] = Nil,
                  settings: => Seq[Def.Setting[_]] = Seq.empty,
                  configurations: Seq[Configuration] = Configurations.default
                  ) = Project(
    id,
    base,
    aggregate,
    dependencies,
    delegates,
    coreDefaultSettings ++ autoImport.nativeRoboVMSettings ++ settings,
    configurations
  )

}
