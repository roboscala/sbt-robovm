package sbtrobovm

import java.util

import org.jboss.shrinkwrap.resolver.api.SBTRoboVMResolver
import org.robovm.compiler.AppCompiler
import org.robovm.compiler.config.Config.{Home, TargetType}
import org.robovm.compiler.config.{Arch, Config, OS, Resource}
import org.robovm.compiler.log.Logger
import org.robovm.compiler.target.ios._
import sbt.Defaults._
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._

object RobovmProjects {

  def baseConfigTask(arch: => Arch, os: => OS, targetType: => TargetType, skipInstall: Boolean) = Def.task[Config.Builder] {
    val t = target.value
    val st = streams.value

    val robovmLogger = new Logger() {
      def debug(s: String, o: java.lang.Object*) = {
        if (robovmVerbose.value) {
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

    val mainClassName = (mainClass in(Compile, run)).value.orElse((selectMainClass in Compile).value).getOrElse(sys.error("Please supply a main class."))
    st.log.debug("Using main class \""+mainClassName+"\"")

    builder.mainClass(mainClassName)
    builder.executableName(executableName.value)
    builder.logger(robovmLogger)

    distHome.value match {
      case null =>
        //Do not set home in that case, RoboVM will try to find it on its own
      case home:File =>
        builder.home(new Config.Home(home))
    }

    robovmProperties.value match {
      case Some(Left(propertyFile)) =>
        st.log.debug("Including properties file: " + propertyFile.getAbsolutePath)
        builder.addProperties(propertyFile)
      case Some(Right(propertyMap)) =>
        st.log.debug("Including properties: " + propertyMap)
        for ((key, value) <- propertyMap) {
          builder.addProperty(key, value)
        }
      case _ =>
    }

    configFile.value foreach { file =>
      st.log.debug("Loading config file: " + file.getAbsolutePath)
      builder.read(file)
    }

    forceLinkClasses.value foreach { pattern =>
      st.log.debug("Including class pattern: " + pattern)
      builder.addForceLinkClass(pattern)
    }

    frameworks.value foreach { framework =>
      st.log.debug("Including framework: " + framework)
      builder.addFramework(framework)
    }

    val homeDir = file(".").getCanonicalPath.stripSuffix("/") + "/"
    for (dir <- nativePath.value) {
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

    robovmInputJars.value foreach { jarFile =>
      st.log.debug("Adding input jar: " + jarFile)
      builder.addClasspathEntry(jarFile)
    }

    robovmResources.value foreach { file =>
      st.log.debug("Including resource: " + file)
      val resource = new Resource(file)
        .skipPngCrush(skipPngCrush.value)
        .flatten(flattenResources.value)
      builder.addResource(resource)
    }

    skipSigning.value foreach builder.iosSkipSigning

    //To make sure that options were not overrided, that would not work.
    builder.skipInstall(skipInstall)
      .targetType(targetType)
      .os(os)
      .arch(arch)

    builder.installDir(t)
    builder.tmpDir(t / "temporary")

    builder
  }

  def launchTask(config: =>Config) = Def.task[Config] {
    val st = streams.value

    st.log.info("Compiling RoboVM app, this could take a while")
    val compiler = new AppCompiler(config)
    compiler.compile()

    st.log.info("RoboVM app compiled")
    config
  }

  lazy val baseSettings = Seq(
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
    distHome := {
      new SBTRoboVMResolver().resolveAndUnpackRoboVMDistArtifact(RoboVMVersion)
    },
    robovmInputJars := (fullClasspath in Compile).value map (_.data),
    robovmVerbose := false,
    robovmLicense := {
      com.robovm.lm.LicenseManager.forkUI()
    }
  )


  object iOSProject {

    def configTask(arch: => Arch, os: => OS, targetType: => TargetType, skipInstall: Boolean) = Def.task[Config] {
      val st = streams.value
      val builder = baseConfigTask(arch,os,targetType,skipInstall).value

      iosSdkVersion.value foreach { version =>
        st.log.debug("Using explicit iOS SDK version: " + version)
        builder.iosSdkVersion(version)
      }

      iosSignIdentity.value foreach { identity =>
        st.log.debug("Using explicit iOS Signing identity: " + identity)
        builder.iosSignIdentity(SigningIdentity.find(SigningIdentity.list(), identity))
      }

      iosProvisioningProfile.value foreach { profile =>
        st.log.debug("Using explicit iOS provisioning profile: " + profile)
        builder.iosProvisioningProfile(ProvisioningProfile.find(ProvisioningProfile.list(), profile))
      }

      iosInfoPlist.value foreach { file =>
        st.log.debug("Using Info.plist file: " + file.getAbsolutePath)
        builder.iosInfoPList(file)
      }

      iosEntitlementsPlist.value foreach { file =>
        st.log.debug("Using Entitlements.plist file: " + file.getAbsolutePath)
        builder.iosEntitlementsPList(file)
      }

      iosResourceRulesPlist.value foreach { file =>
        st.log.debug("Using ResourceRules.plist file: " + file.getAbsolutePath)
        builder.iosResourceRulesPList(file)
      }

      builder.build()
    }

    lazy val iosSettings = Seq(
      iosSdkVersion := None,
      iosSignIdentity := None,
      iosProvisioningProfile := None,
      iosInfoPlist := None,
      iosEntitlementsPlist := None,
      iosResourceRulesPlist := None,
      simulatorDevice := None,
      device := {
        val config = launchTask(configTask(Arch.thumbv7, OS.ios, TargetType.ios, skipInstall = true).value).value

        val launchParameters = config.getTarget.createLaunchParameters()
        config.getTarget.launch(launchParameters).waitFor()
      },
      iphoneSim := {
        val config = launchTask(configTask(Arch.x86, OS.ios, TargetType.ios, skipInstall = true).value).value

        val launchParameters = config.getTarget.createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        launchParameters.setDeviceType(DeviceType.getBestDeviceType(config.getHome, DeviceType.DeviceFamily.iPhone))
        config.getTarget.launch(launchParameters).waitFor()
      },
      ipadSim := {
        val config = launchTask(configTask(Arch.x86, OS.ios, TargetType.ios, skipInstall = true).value).value

        val launchParameters = config.getTarget.createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        launchParameters.setDeviceType(DeviceType.getBestDeviceType(config.getHome, DeviceType.DeviceFamily.iPad))
        config.getTarget.launch(launchParameters).waitFor()
      },
      ipa := {
        val config = configTask(arch = null, OS.ios, TargetType.ios, skipInstall = false).value
        val compiler = new AppCompiler(config)
        val architectures = new util.ArrayList[Arch]()
        architectures.add(Arch.thumbv7)
        architectures.add(Arch.arm64)
        compiler.createIpa(architectures)
      },
      simulator := {
        val simulatorDeviceName: String = simulatorDevice.value.getOrElse(sys.error("Define device kind name first. See simulator-device setting and simulator-devices task."))
        val config = launchTask(configTask(Arch.x86, OS.ios, TargetType.ios, skipInstall = true).value).value

        val launchParameters = config.getTarget.createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        val simDevice = DeviceType.getDeviceType(config.getHome, simulatorDeviceName)
        if (simDevice == null) sys.error( s"""iOS simulator device "$simulatorDeviceName" not found.""")
        launchParameters.setDeviceType(simDevice)
        config.getTarget.launch(launchParameters).waitFor()
      },
      simulatorDevices := {
        val home = Option(distHome.value).collect {
          case file => new Home(file)
        }.getOrElse(Home.find())

        val devices = DeviceType.getSimpleDeviceTypeIds(home)
        for (simpleDevice <- scala.collection.convert.wrapAsScala.iterableAsScalaIterable(devices)) {
          println(simpleDevice)
        }
        println(devices.size() + " devices found.")
      }
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
      coreDefaultSettings ++ baseSettings ++ iosSettings ++ settings,
      configurations
    )
  }

  object NativeProject {

    lazy val nativeSettings = Seq(
      native := {
        val config = launchTask(baseConfigTask(Arch.getDefaultArch, OS.getDefaultOS, TargetType.console, skipInstall = true).value.build()).value

        val launchParameters = config.getTarget.createLaunchParameters()
        config.getTarget.launch(launchParameters).waitFor()
      }
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
      coreDefaultSettings ++ baseSettings ++ nativeSettings ++ settings,
      configurations
    )
  }

}
