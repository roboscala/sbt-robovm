package sbtrobovm

import sbt._
import Keys._
import Defaults._
import RobovmPlugin._

import org.robovm.compiler.AppCompiler
import org.robovm.compiler.config.Arch
import org.robovm.compiler.config.Config
import org.robovm.compiler.config.Config.TargetType
import org.robovm.compiler.config.OS
import org.robovm.compiler.config.Resource
import org.robovm.compiler.log.Logger
import org.robovm.compiler.target.ios.IOSSimulatorLaunchParameters
import org.robovm.compiler.target.ios.IOSTarget
import org.robovm.compiler.target.ios.SigningIdentity
import org.robovm.compiler.target.ios.ProvisioningProfile

object RobovmProjects {
  object Standard {
    private val build = TaskKey[BuildSettings]("_aux_build")
    private val iosBuild = TaskKey[IosBuildSettings]("_aux_ios_build")

    case class BuildSettings(
      executableName: String,
      propertiesFile: Option[File],
      configFile: Option[File],
      forceLinkClasses: Seq[String],
      frameworks: Seq[String],
      nativePath: Seq[File],
      fullClasspath: Classpath,
      unmanagedResources: Seq[File],
      skipPngCrush: Boolean,
      flattenResources: Boolean,
      mainClass: Option[String],
      distHome: Option[File]
    )

    case class IosBuildSettings(
      sdkVersion: Option[String],
      signIdentity: Option[String],
      provisioningProfile: Option[String],
      infoPlist: Option[File],
      entitlementsPlist: Option[File],
      resourceRulesPlist: Option[File]
    )

    def launchTask(arch: Arch, os: OS, targetType: TargetType, skipInstall: Boolean, launcher: Config => Unit) = (target, build, iosBuild, streams) map {
      (t, b, ios, st) => {
        val robovmLogger = new Logger() {
          def debug(s: String, o: java.lang.Object*) = st.log.debug(s.format(o:_*))
          def info(s: String, o: java.lang.Object*) = st.log.info(s.format(o:_*))
          def warn(s: String, o: java.lang.Object*) = st.log.warn(s.format(o:_*))
          def error(s: String, o: java.lang.Object*) = st.log.error(s.format(o:_*))
        }

        val builder = new Config.Builder()

        builder.mainClass(b.mainClass.getOrElse("Main"))
          .executableName(b.executableName)
          .logger(robovmLogger)
          .skipInstall(skipInstall)
          .targetType(targetType)
          .os(os)
          .arch(arch)

        b.distHome map { file =>
          builder.home(new Config.Home(file))
        }

        b.propertiesFile map { file =>
          st.log.debug("Including properties file: " + file.getAbsolutePath())
          builder.addProperties(file)
        }

        b.configFile map { file =>
          st.log.debug("Loading config file: " + file.getAbsolutePath())
          builder.read(file)
        }

        b.forceLinkClasses foreach { pattern =>
          st.log.debug("Including class pattern: " + pattern)
          builder.addForceLinkClass(pattern)
        }

        b.frameworks foreach { framework =>
          st.log.debug("Including framework: " + framework)
          builder.addFramework(framework)
        }

        for (dir <- b.nativePath if dir.isDirectory) {
          dir.listFiles foreach { lib =>
              st.log.debug("Including lib: " + lib)
              builder.addLib(new Config.Lib(lib.getName, true))
          }
        }

        b.fullClasspath.map(i => i.data) foreach { file =>
          st.log.debug("Including classpath item: " + file)
          builder.addClasspathEntry(file)
        }

        b.unmanagedResources foreach { file =>
          st.log.debug("Including resource: " + file)
          val resource = new Resource(file)
            .skipPngCrush(b.skipPngCrush)
            .flatten(b.flattenResources)
          builder.addResource(resource)
        }

        ios.sdkVersion map { version =>
          st.log.debug("Using explicit iOS SDK version: " + version)
          builder.iosSdkVersion(version)
        }

        ios.signIdentity map { identity =>
          st.log.debug("Using explicit iOS Signing identity: " + identity)
          builder.iosSignIdentity(SigningIdentity.find(SigningIdentity.list(), identity))
        }

        ios.provisioningProfile map { profile =>
          st.log.debug("Using explicit iOS provisioning profile: " + profile)
          builder.iosProvisioningProfile(ProvisioningProfile.find(ProvisioningProfile.list(), profile))
        }

        ios.infoPlist map { file =>
          st.log.debug("Using Info.plist file: " + file.getAbsolutePath())
          builder.iosInfoPList(file)
        }

        ios.entitlementsPlist map { file =>
          st.log.debug("Using Entitlements.plist file: " + file.getAbsolutePath())
          builder.iosEntitlementsPList(file)
        }

        ios.resourceRulesPlist map { file =>
          st.log.debug("Using ResourceRules.plist file: " + file.getAbsolutePath())
          builder.iosResourceRulesPList(file)
        }

        builder.installDir(t)
        builder.tmpDir(t / "native")

        st.log.info("Compiling RoboVM app, this could take a while")
        val config = builder.build()
        val compiler = new AppCompiler(config)
        compiler.compile()

        st.log.info("Launching RoboVM app")
        launcher(config)
      }
    }

    private val nativeTask = launchTask(Arch.getDefaultArch, OS.getDefaultOS, TargetType.console, true, (config) => {
      val launchParameters = config.getTarget().createLaunchParameters()
      config.getTarget().launch(launchParameters).waitFor()
    })

    private val deviceTask = launchTask(Arch.thumbv7, OS.ios, TargetType.ios, true, (config) => {
        val launchParameters = config.getTarget().createLaunchParameters()
        config.getTarget().launch(launchParameters).waitFor()
    })

    private val iphoneSimTask = launchTask(Arch.x86, OS.ios, TargetType.ios, true, (config) => {
        val launchParameters = config.getTarget().createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        launchParameters.setFamily(IOSSimulatorLaunchParameters.Family.iPhoneRetina4Inch)
        config.getTarget().launch(launchParameters).waitFor()
    })

    private val ipadSimTask = launchTask(Arch.x86, OS.ios, TargetType.ios, true, (config) => {
        val launchParameters = config.getTarget().createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        launchParameters.setFamily(IOSSimulatorLaunchParameters.Family.iPadRetina)
        config.getTarget().launch(launchParameters).waitFor()
    })

    private val ipaTask = launchTask(Arch.thumbv7, OS.ios, TargetType.ios, false, (config) => {
      config.getTarget().asInstanceOf[IOSTarget].createIpa()
    })

    lazy val robovmSettings = Seq(
      libraryDependencies ++= Seq(
        "org.robovm" % "robovm-rt" % "0.0.10",
        "org.robovm" % "robovm-objc" % "0.0.10",
        "org.robovm" % "robovm-cocoatouch" % "0.0.10",
        "org.robovm" % "robovm-cacerts-full" % "0.0.10"
      ),
      build <<= (executableName, propertiesFile, configFile, forceLinkClasses, frameworks, nativePath, fullClasspath in Compile, unmanagedResources in Compile, skipPngCrush, flattenResources, mainClass in (Compile, run), distHome) map BuildSettings,
      iosBuild <<= (iosSdkVersion, iosSignIdentity, iosProvisioningProfile, iosInfoPlist, iosEntitlementsPlist, iosResourceRulesPlist) map IosBuildSettings,
      executableName := "RoboVM App",
      forceLinkClasses := Seq.empty,
      frameworks := Seq.empty,
      nativePath := Seq.empty,
      skipPngCrush := false,
      flattenResources := false,
      propertiesFile := None,
      configFile := None,
      distHome := None,
      iosSdkVersion := None,
      iosSignIdentity := None,
      iosProvisioningProfile := None,
      iosInfoPlist := None,
      iosEntitlementsPlist := None,
      iosResourceRulesPlist := None,
      device <<= deviceTask dependsOn (compile in Compile),
      iphoneSim <<= iphoneSimTask dependsOn (compile in Compile),
      ipadSim <<= ipadSimTask dependsOn (compile in Compile),
      ipa <<= ipaTask dependsOn (compile in Compile),
      native <<= nativeTask dependsOn (compile in Compile)
    )

    def apply(
      id: String,
      base: File,
      aggregate: => Seq[ProjectReference] = Nil,
      dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
      delegates: => Seq[ProjectReference] = Nil,
      settings: => Seq[sbt.Project.Setting[_]] = Seq.empty,
      configurations: Seq[Configuration] = Configurations.default
    ) = Project(
      id,
      base,
      aggregate,
      dependencies,
      delegates,
      defaultSettings ++ robovmSettings ++ settings,
      configurations
    )
  }
}
