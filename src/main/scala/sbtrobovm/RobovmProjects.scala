package sbtrobovm

import org.robovm.compiler.AppCompiler
import org.robovm.compiler.config.Config.TargetType
import org.robovm.compiler.config.{Arch, Config, OS, Resource}
import org.robovm.compiler.log.Logger
import org.robovm.compiler.target.ios._
import sbt.Defaults._
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._

object RobovmProjects {

  object Standard {
    private val build = TaskKey[BuildSettings]("_aux_build")
    private val iosBuild = TaskKey[IosBuildSettings]("_aux_ios_build")

    case class BuildSettings(
                              executableName: String,
                              properties: Option[Either[File, Map[String, String]]],
                              configFile: Option[File],
                              skipSigning: Option[Boolean],
                              forceLinkClasses: Seq[String],
                              frameworks: Seq[String],
                              nativePath: Seq[File],
                              fullClasspath: Classpath,
                              unmanagedResources: Seq[File],
                              skipPngCrush: Boolean,
                              flattenResources: Boolean,
                              mainClass: Option[String],
                              distHome: Option[File],
                              alternativeJars: Option[Seq[File]],
                              debug: Boolean
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
          def debug(s: String, o: java.lang.Object*) = {
            if (b.debug) {
              st.log.info(s.format(o: _*))
            } else {
              st.log.debug(s.format(o: _*))
            }
          }

          def info(s: String, o: java.lang.Object*) = st.log.info(s.format(o: _*))

          def warn(s: String, o: java.lang.Object*) = st.log.warn(s.format(o: _*))

          def error(s: String, o: java.lang.Object*) = st.log.error(s.format(o: _*))
        }

        val builder = new Config.Builder()

        builder.mainClass(b.mainClass.getOrElse("Main"))
          .executableName(b.executableName)
          .logger(robovmLogger)

        b.distHome map { file =>
          builder.home(new Config.Home(file))
        }

        b.properties match {
          case Some(Left(file)) =>
            st.log.debug("Including properties file: " + file.getAbsolutePath)
            builder.addProperties(file)
          case Some(Right(map)) =>
            st.log.debug("Including properties: " + map)
            for ((key, value) <- map) {
              builder.addProperty(key, value)
            }
          case _ =>
        }

        b.configFile map { file =>
          st.log.debug("Loading config file: " + file.getAbsolutePath)
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

        val homeDir = file(".").getCanonicalPath.stripSuffix("/") + "/"
        for (dir <- b.nativePath) {
          if (dir.isDirectory) {
            dir.listFiles foreach { lib =>
              if (lib.isFile && !lib.isHidden) {
                val libRelativePath = lib.getCanonicalPath.stripPrefix(homeDir)

                st.log.debug("Including lib: " + libRelativePath)

                builder.addLib(new Config.Lib(libRelativePath, true))
              }
            }
          } else {
            st.log.warn(s"Natives directory '${dir.getAbsolutePath}' doesn't exist.")
          }
        }

        b.alternativeJars match {
          case Some(alternativeFiles: Seq[File]) =>
            alternativeFiles foreach { file =>
              st.log.debug("Including alternative classpath item: " + file)
              builder.addClasspathEntry(file)
            }
          case None =>
            b.fullClasspath.map(i => i.data) foreach { file =>
              st.log.debug("Including classpath item: " + file)
              builder.addClasspathEntry(file)
            }
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
          st.log.debug("Using Info.plist file: " + file.getAbsolutePath)
          builder.iosInfoPList(file)
        }

        ios.entitlementsPlist map { file =>
          st.log.debug("Using Entitlements.plist file: " + file.getAbsolutePath)
          builder.iosEntitlementsPList(file)
        }

        ios.resourceRulesPlist map { file =>
          st.log.debug("Using ResourceRules.plist file: " + file.getAbsolutePath)
          builder.iosResourceRulesPList(file)
        }

        b.skipSigning foreach builder.iosSkipSigning

        //To make sure that options were not overrided, that would not work.
        builder.skipInstall(skipInstall)
          .targetType(targetType)
          .os(os)
          .arch(arch)

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

    private val nativeTask = launchTask(Arch.getDefaultArch, OS.getDefaultOS, TargetType.console, skipInstall = true, (config) => {
      val launchParameters = config.getTarget.createLaunchParameters()
      config.getTarget.launch(launchParameters).waitFor()
    })

    private val deviceTask = launchTask(Arch.thumbv7, OS.ios, TargetType.ios, skipInstall = true, (config) => {
      val launchParameters = config.getTarget.createLaunchParameters()
      config.getTarget.launch(launchParameters).waitFor()
    })

    private val iphoneSimTask = launchTask(Arch.x86, OS.ios, TargetType.ios, skipInstall = true, (config) => {
      val launchParameters = config.getTarget.createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
      launchParameters.setDeviceType(DeviceType.getBestDeviceType(config.getHome,DeviceType.DeviceFamily.iPhone))
      config.getTarget.launch(launchParameters).waitFor()
    })

    private val ipadSimTask = launchTask(Arch.x86, OS.ios, TargetType.ios, skipInstall = true, (config) => {
      val launchParameters = config.getTarget.createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
      launchParameters.setDeviceType(DeviceType.getBestDeviceType(config.getHome,DeviceType.DeviceFamily.iPad))
      config.getTarget.launch(launchParameters).waitFor()
    })

    private val ipaTask = launchTask(Arch.thumbv7, OS.ios, TargetType.ios, skipInstall = false, (config) => {
      config.getTarget.asInstanceOf[IOSTarget].createIpa()
    })

    lazy val robovmSettings = Seq(
      build := BuildSettings(executableName.value, robovmProperties.value, configFile.value, skipSigning.value, forceLinkClasses.value, frameworks.value, nativePath.value, (fullClasspath in Compile).value, robovmResources.value, skipPngCrush.value, flattenResources.value, (mainClass in(Compile, run)).value, distHome.value, alternativeInputJars.value, robovmDebug.value),
      iosBuild <<= (iosSdkVersion, iosSignIdentity, iosProvisioningProfile, iosInfoPlist, iosEntitlementsPlist, iosResourceRulesPlist) map IosBuildSettings,
      executableName := "RoboVM App",
      forceLinkClasses := Seq.empty,
      frameworks := Seq.empty,
      nativePath := Seq.empty,
      robovmResources := (unmanagedResources in Compile).value,
      skipPngCrush := false,
      flattenResources := false,
      robovmProperties := None,
      configFile := None,
      skipSigning := None,
      distHome := None,
      alternativeInputJars := None,
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
      native <<= nativeTask dependsOn (compile in Compile),
      robovmDebug := false
    )

    def apply(
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
      coreDefaultSettings ++ robovmSettings ++ settings,
      configurations
    )
  }

}
