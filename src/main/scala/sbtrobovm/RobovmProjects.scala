package sbtrobovm

import java.io.{IOException, StringReader}

import org.apache.commons.io.FileUtils
import org.jboss.shrinkwrap.resolver.api.SBTRoboVMResolver
import org.robovm.compiler.AppCompiler
import org.robovm.compiler.config.{Arch, Config, OS}
import org.robovm.compiler.log.Logger
import org.robovm.compiler.target.ConsoleTarget
import org.robovm.compiler.target.ios._
import org.robovm.libimobiledevice.IDevice
import sbt.Defaults._
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._

object RobovmProjects {

  type TargetType = String

  def configTask(arch: Def.Initialize[Array[Arch]], os: => OS, targetType: => TargetType, skipInstall: Boolean, scope:Scope) = Def.task[Config.Builder] {
    val st = streams.value

    val builder = new Config.Builder()

    builder.logger(new Logger() {
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
    })

    builder.home(robovmHome.value)

    robovmProperties.value match {
      case Left(propertyFile) =>
        st.log.debug("Including properties file: " + propertyFile.getAbsolutePath)
        builder.addProperties(propertyFile)
      case Right(propertyMap) =>
        st.log.debug("Including embedded properties: " + propertyMap)
        for ((key, value) <- propertyMap) {
          builder.addProperty(key, value)
        }
      case _ =>
    }

    robovmConfiguration.value match {
      case Left(file) =>
        st.log.debug("Loading config file: " + file.getAbsolutePath)
        builder.read(file)
      case Right(xml) =>
        st.log.debug("Loading embedded xml configuration: "+xml)
        builder.read(new StringReader(xml.toString()),baseDirectory.value)
    }

    builder.clearClasspathEntries()
    robovmInputJars.value foreach { jarFile =>
      st.log.debug("Adding input jar: " + jarFile)
      builder.addClasspathEntry(jarFile)
    }

    //To make sure that options were not overrided, that would not work.
    builder.skipInstall(skipInstall)
    builder.targetType(targetType)
    builder.os(os)
    builder.archs(arch.value:_*)

    val t = target.value
    builder.installDir(t / "robovm")
    val tmpDir = t / "robovm.tmp"
    if(tmpDir.isDirectory){
      try {
        FileUtils.deleteDirectory(tmpDir)
      } catch {
        case io:IOException =>
          st.log.error("Failed to clean temporary output directory "+tmpDir)
          st.log.trace(io)
      }
    }
    tmpDir.mkdirs()
    builder.tmpDir(tmpDir)

    val enableRobovmDebug = (robovmDebug in scope).value
    if(enableRobovmDebug){
      builder.debug(true)
      val debugPort = (robovmDebugPort in scope).value
      if(debugPort != -1){
        st.log.info("RoboVM Debug is enabled on port "+debugPort)
        builder.addPluginArgument("debug:jdwpport=" + debugPort)
      }else{
        st.log.warn("RoboVM Debug is enabled, but no port is specified!")
      }
    }

    builder
  }

  def buildTask(configBuilderTask:Def.Initialize[Task[Config.Builder]]) = Def.task[(Config, AppCompiler)] {
    val st = streams.value

    val configBuilder = configBuilderTask.value
    try{
      configBuilder.write(target.value / "LastRobovm.xml")
      st.log.debug("Written LastRobovm.xml (for debug)")
    }catch {
      case e:Exception =>
        st.log.debug("Failed to write LastRobovm.xml (for debug) "+e)
    }
    val config = configBuilder.build()

    st.log.info("Compiling RoboVM app, this could take a while")
    val compiler = new AppCompiler(config)
    compiler.build()

    st.log.info("RoboVM app compiled")
    (config, compiler)
  }

  lazy val baseSettings = Seq(
    robovmProperties := Right(
      Map(
        "app.name" -> name.value,
        "app.executable" -> name.value.replace(" ",""),
        "app.mainclass" -> {
          val mainClassName = (mainClass in(Compile, run)).value.orElse((selectMainClass in Compile).value).getOrElse("")
          streams.value.log.debug("Selected main class \""+mainClassName+"\"")
          mainClassName
        })
    ),
    robovmConfiguration := Right(
      <config>
        <executableName>${{app.executable}}</executableName>
        <mainClass>${{app.mainclass}}</mainClass>
        <resources>
          <resource>
            <directory>resources</directory>
          </resource>
        </resources>
      </config>
    ),
    skipSigning := None,
    robovmDebug := false,
    robovmDebug in Debug := true,
    robovmDebugPort := -1,
    robovmDebugPort in Debug := 5005,
    robovmHome := new Config.Home(new SBTRoboVMResolver(streams.value.log).resolveAndUnpackRoboVMDistArtifact(RoboVMVersion)),
    robovmInputJars := (fullClasspath in Compile).value map (_.data),
    robovmVerbose := false,
    robovmTarget64bit := false,
    robovmLicense := {
      com.robovm.lm.LicenseManager.forkUI()
    },
    ivyConfigurations += ManagedNatives
  )

  lazy val Debug = config("debug")

  trait RoboVMProject {

    val projectSettings:Seq[Def.Setting[_]]

    def apply(
               id: String,
               base: File,
               aggregate: => Seq[ProjectReference] = Nil,
               dependencies: => Seq[ClasspathDep[ProjectReference]] = Nil,
               delegates: => Seq[ProjectReference] = Nil,
               settings: => Seq[Def.Setting[_]] = Seq.empty,
               configurations: Seq[Configuration] = Configurations.default :+ Debug
               ) = Project(
      id,
      base,
      aggregate,
      dependencies,
      delegates,
      coreDefaultSettings ++ baseSettings ++ projectSettings ++ settings,
      configurations
    )
  }

  object iOSProject extends RoboVMProject {

    val lastUsedDeviceFile = settingKey[File]("A file to save last used (non-preferred) device ID to. Can be null to disable this.")

    def configIOSTask(configBuilderTask:Def.Initialize[Task[Config.Builder]], scope: Scope) = Def.task[Config.Builder]{
      val st = streams.value
      val builder = configBuilderTask.value

      (provisioningProfile in scope).value.foreach(name => {
        val list = ProvisioningProfile.list()
        try{
          val profile = ProvisioningProfile.find(list,name)
          builder.iosProvisioningProfile(profile)
          st.log.debug("Using explicit provisioning profile: "+profile.toString)
        }catch {
          case _:IllegalArgumentException => // Not found
            st.log.error("No provisioning profile identifiable with \""+name+"\" found. "+{
              if(list.size() == 0){
                "No profiles installed."
              }else{
                "Those were found: ("+list.size()+")"
              }
            })
            for(i <- 0 until list.size()){
              val profile = list.get(i)
              st.log.error("\tName: "+profile.getName)
              st.log.error("\t\tUUID: "+profile.getUuid)
              st.log.error("\t\tAppID Prefix: "+profile.getAppIdPrefix)
              st.log.error("\t\tAppID Name: "+profile.getAppIdName)
              st.log.error("\t\tType: "+profile.getType)
            }
        }
      })

      val skipSign = skipSigning.value.exists(skip => skip) //Check if value is Some(true)

      if(skipSign){
        st.log.debug("Skipping signing.")
        builder.iosSkipSigning(true)
      }else{
        (signingIdentity in scope).value.foreach(name => {
          val list = SigningIdentity.list()
          try{
            val signIdentity = SigningIdentity.find(list,name)
            builder.iosSignIdentity(signIdentity)
            st.log.debug("Using explicit signing identity: "+signIdentity.toString)
          }catch {
            case _:IllegalArgumentException => // Not found
              st.log.error("No signing identity identifiable with \""+name+"\" found. "+{
                if(list.size() == 0){
                  "No identities installed."
                }else{
                  "Those were found: ("+list.size()+")"
                }
              })
              for(i <- 0 until list.size()){
                val identity = list.get(i)
                st.log.error("\tName: "+identity.getName)
                st.log.error("\t\tFingerprint: "+identity.getFingerprint)
              }
          }
        })
      }

      builder
    }

    private def simulatorArchitectureSetting(scope:Scope) = Def.setting[Array[Arch]]{
      Array(if((robovmTarget64bit in scope).value)Arch.x86_64 else Arch.x86)
    }

    def buildSimulatorTask(scope:Scope) = Def.task[(Config, AppCompiler)]{
      buildTask(
        configIOSTask(
          configTask(simulatorArchitectureSetting(scope), OS.ios, IOSTarget.TYPE, skipInstall = true, scope),
          scope
        )
      ).value
    }

    def runSimulator(config:Config,compiler:AppCompiler, device:DeviceType):Int = {
      val launchParameters = config.getTarget.createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
      launchParameters.setDeviceType(device)

      //TODO Stdout and stderr fifos

      compiler.launch(launchParameters)
    }

    private val deviceArchitectureSetting = Def.setting[Array[Arch]]{
      Array(if((robovmTarget64bit in device).value)Arch.arm64 else Arch.thumbv7)
    }

    private val ipaArchitectureSetting = Def.setting[Array[Arch]]{Array(Arch.thumbv7, Arch.arm64)}

    private def deviceTask(scope:Scope) = Def.task[Unit]{
      val log = streams.value.log
      val (config, compiler) = buildTask(configIOSTask(configTask(deviceArchitectureSetting, OS.ios, IOSTarget.TYPE, skipInstall = true, scope), device.scope)).value

      val launchParameters = config.getTarget.createLaunchParameters()

      launchParameters match {
        case iLP: IOSDeviceLaunchParameters =>
          val prefDevices = preferredDevices.value
          val lastUsedIDFile = lastUsedDeviceFile.value
          val devices = IDevice.listUdids()
          if (devices.length == 1) {
            if(lastUsedDeviceFile != null && !prefDevices.contains(devices(0))){
              //Save this device, in case of more becoming available later
              FileUtils.write(lastUsedIDFile, devices(0), false)
              log.debug("Saved the last used device ID")
            }
            iLP.setDeviceId(devices(0)) //So it does not have to be listed multiple times
          }else if(devices.length > 1){
            prefDevices.find(preferredDevice => devices.contains(preferredDevice)) match {
              case Some(foundPreferredDeviceID) =>
                iLP.setDeviceId(foundPreferredDeviceID)
              case None =>
                var deviceSet = false
                if(lastUsedIDFile != null && lastUsedIDFile.isFile){
                  //No preferred device found, but there is last used ID file, let's try that
                  val lastID = FileUtils.readFileToString(lastUsedIDFile)
                  if(devices.contains(lastID)){
                    iLP.setDeviceId(lastID)
                    log.debug("No preferred device connected, using most recent one.")
                    deviceSet = true
                  }
                }
                if(!deviceSet)log.debug("Multiple devices found and none is in preferredDevices")
            }
          }//Don't do anything if no devices detected, maybe launcher will be luckier
        case _ =>
          log.debug("Launch parameters are not IOSDeviceLaunchParameters")
      }

      val code = compiler.launch(launchParameters)
      log.debug("device task finished (exit code "+code+")")
    }

    private def simulatorTask(scope:Scope, deviceType: =>DeviceType) = Def.task[Unit]{
      val device = if (deviceType != null) deviceType else {
        val simulatorDeviceName: String = (robovmSimulatorDevice in scope).value.getOrElse(sys.error("Define device kind name first. See robovmSimulatorDevice setting and simulatorDevices task."))
        val device = DeviceType.getDeviceType(simulatorDeviceName)
        if (device == null) sys.error( s"""iOS simulator device "$simulatorDeviceName" not found.""")
        device
      }
      val (config,compiler) = buildSimulatorTask(scope).value
      val code = runSimulator(config, compiler, device)
      streams.value.log.debug("simulator task finished (exit code "+code+")")
    }

    override lazy val projectSettings = Seq(
      robovmSimulatorDevice := None,
      provisioningProfile := None,
      signingIdentity := None,
      preferredDevices := Nil,
      lastUsedDeviceFile := target.value / "LastUsediOSDevice.txt",
      device := deviceTask(device.scope).value,
      device in Debug := deviceTask(device.scope.in(Debug)).value,
      //TODO Allow specifying SDK version and device version in simulator tasks?
      iphoneSim := simulatorTask(iphoneSim.scope, DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPhone)).value,
      iphoneSim in Debug := simulatorTask(iphoneSim.scope.in(Debug), DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPhone)).value,
      ipadSim := simulatorTask(ipadSim.scope, DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPad)).value,
      ipadSim in Debug := simulatorTask(ipadSim.scope.in(Debug), DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPad)).value,
      simulator := simulatorTask(simulator.scope, null).value,
      simulator in Debug := simulatorTask(simulator.scope.in(Debug), null).value,
      ipa := {
        val (_, compiler) = buildTask(configIOSTask(configTask(ipaArchitectureSetting, OS.ios, IOSTarget.TYPE, skipInstall = false, ipa.scope), ipa.scope)).value
        compiler.archive()
      },
      simulatorDevices := {
        val devices = DeviceType.getSimpleDeviceTypeIds
        for (simpleDevice <- scala.collection.convert.wrapAsScala.iterableAsScalaIterable(devices)) {
          println(simpleDevice)
        }
        println(devices.size() + " devices found.")
      }
    )

  }

  object NativeProject extends RoboVMProject {

    val robovmTargetArchitecture = settingKey[Array[Arch]]("Architecture(s) targeted by NativeProject")

    private def nativeTask(scope:Scope, buildOnly:Boolean) = Def.task[Unit]{
      val (config, compiler) = buildTask(configTask(robovmTargetArchitecture, OS.getDefaultOS, ConsoleTarget.TYPE, skipInstall = true, scope)).value

      if(buildOnly){
        compiler.install()
        streams.value.log.debug("nativeBuild task finished")
      }else{
        val launchParameters = config.getTarget.createLaunchParameters()
        val code = compiler.launch(launchParameters)
        streams.value.log.debug("native task finished (exit code "+code+")")
      }
    }


    override lazy val projectSettings = Seq(
      robovmTargetArchitecture := Array(Arch.getDefaultArch),
      native := nativeTask(native.scope, buildOnly = false).value,
      native in Debug := nativeTask(native.scope.in(Debug), buildOnly = false).value,
      nativeBuild := nativeTask(native.scope, buildOnly = true).value,
      nativeBuild in Debug := nativeTask(native.scope.in(Debug), buildOnly = true).value
    )
    
  }
}
