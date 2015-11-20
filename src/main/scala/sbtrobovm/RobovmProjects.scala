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
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._
import sbtrobovm.interfacebuilder.RobovmInterfaceBuilder

object RobovmProjects {

  type TargetType = String

  def configTask(arch: Def.Initialize[Array[Arch]], os: Def.Initialize[Option[OS]], targetType: TargetType, skipInstall: Boolean, scope:Scope) = Def.task[Config.Builder] {
    val log = (streams in scope).value.log
    log.debug("configTask - Configuring for scope: "+scope)

    val builder = new Config.Builder()
    builder.logger(robovmCompilerLogger.value)
    builder.home(robovmHome.value)

    robovmProperties.value match {
      case Left(propertyFile) =>
        log.debug("Including properties file: " + propertyFile.getAbsolutePath)
        builder.addProperties(propertyFile)
      case Right(propertyMap) =>
        log.debug("Including embedded properties: " + propertyMap)
        for ((key, value) <- propertyMap) {
          builder.addProperty(key, value)
        }
      case _ =>
    }

    robovmConfiguration.value match {
      case Left(file) =>
        log.debug("Loading config file: " + file.getAbsolutePath)
        builder.read(file)
      case Right(xml) =>
        log.debug("Loading embedded xml configuration: "+xml)
        builder.read(new StringReader(xml.toString()),baseDirectory.value)
    }

    builder.clearClasspathEntries()
    robovmInputJars.value foreach { jarFile =>
      log.debug("Adding input jar: " + jarFile)
      builder.addClasspathEntry(jarFile)
    }

    //To make sure that options were not overrided, that would not work.
    builder.skipInstall(skipInstall)
    builder.targetType(targetType)
    os.value match {
      case Some(selectedOS) =>
        builder.os(selectedOS)
        log.debug("Setting OS to "+selectedOS)
      case None =>
        log.debug("OS not specified")
    }
    builder.archs(arch.value:_*)

    val t = target.value
    builder.installDir(t / "robovm")
    val tmpDir = t / "robovm.tmp"
    if(tmpDir.isDirectory){
      try {
        FileUtils.deleteDirectory(tmpDir)
      } catch {
        case io:IOException =>
          log.error("Failed to clean temporary output directory "+tmpDir)
          log.trace(io)
      }
    }
    tmpDir.mkdirs()
    builder.tmpDir(tmpDir)

    val enableRobovmDebug = (robovmDebug in scope).value
    if(enableRobovmDebug){
      builder.debug(true)
      val debugPort = (robovmDebugPort in scope).value
      if(debugPort != -1){
        log.info("RoboVM Debug is enabled on port "+debugPort)
        builder.addPluginArgument("debug:jdwpport=" + debugPort)
      }else{
        log.warn("RoboVM Debug is enabled, but no port is specified!")
      }
    }

    builder.enableBitcode((robovmBitcode in scope).value)

    builder
  }

  def validateConfig(config:Config, log:sbt.Logger): Unit ={
    import scala.collection.convert.wrapAll._
    for(res <- config.getResources){
      if(res.getPath != null){
        if(!res.getPath.exists())log.warn("Config Validation: Resource file \""+res.getPath.getCanonicalPath+"\" does not exist")
      }else if(res.getDirectory != null){
        if(!res.getDirectory.exists())log.warn("Config Validation: Resource directory \""+res.getDirectory.getCanonicalPath+"\" does not exist")
      }else{
        log.warn("Config Validation: Resource has neither directory nor file, this should not happen. ("+res.toString+")")
      }
    }
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
    if(robovmValidateConfig.value)validateConfig(config, st.log)

    st.log.info("Compiling RoboVM app, this could take a while")
    val compiler = new AppCompiler(config)
    compiler.build()

    st.log.info("RoboVM app compiled")
    (config, compiler)
  }

  lazy val baseSettings = Seq(
    resolvers ++= {if (RoboVMVersion.contains("-SNAPSHOT")) Seq(Resolver.sonatypeRepo("snapshots")) else Seq()},
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
    robovmSkipSigning := None,
    robovmDebug := false,
    robovmDebug in Debug := true,
    robovmDebugPort := -1,
    robovmDebugPort in Debug := 5005,
    robovmHome := new Config.Home(new SBTRoboVMResolver(streams.value.log).resolveAndUnpackRoboVMDistArtifact(RoboVMVersion)),
    robovmInputJars := (fullClasspath in Compile).value map (_.data),
    robovmVerbose := false,
    robovmValidateConfig := true,
    robovmBitcode := true,
    robovmCompilerLogger := new Logger() {

     val log = (streams in robovmCompilerLogger).value.log

      def debug(s: String, o: java.lang.Object*) = {
        if (robovmVerbose.value) {
          log.info(s.format(o: _*))
        } else {
          log.debug(s.format(o: _*))
        }
      }

      def info(s: String, o: java.lang.Object*) = log.info(s.format(o: _*))

      def warn(s: String, o: java.lang.Object*) = log.warn(s.format(o: _*))

      def error(s: String, o: java.lang.Object*) = log.error(s.format(o: _*))
    },
    robovmTarget64bit := false,
    ivyConfigurations += ManagedNatives,
    ivyConfigurations += Debug
  )

  lazy val toolSettings = Seq(
    robovmLicense := {
      RobovmInterfaceBuilder.clearIBProxyCreationFailures()
      com.robovm.lm.LicenseManager.forkUI()
    },
    simulatorDevices := {
      //This tool needs working iOS project configuration, which is not present by default
      //So by default, show just some help
      println("Launch this in a configured iOS project")
    }
  )

  lazy val Debug = config("debug")

  //region iOS
  val lastUsedDeviceFile = settingKey[File]("A file to save last used (non-preferred) device ID to. Can be null to disable this.")

  def configIOSTask(configBuilderTask:Def.Initialize[Task[Config.Builder]], scope: Scope) = Def.task[Config.Builder]{
    val st = streams.value
    val builder = configBuilderTask.value

    (robovmProvisioningProfile in scope).value.foreach(name => {
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

    val skipSign = robovmSkipSigning.value.exists(skip => skip) //Check if value is Some(true)

    if(skipSign){
      st.log.debug("Skipping signing.")
      builder.iosSkipSigning(true)
    }else{
      (robovmSigningIdentity in scope).value.foreach(name => {
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

  def buildSimulatorTask(scope:Scope) = Def.task[(Config, AppCompiler)]{
    buildTask(
      configIOSTask(
        configTask(robovmTargetArch in simulator, robovmTargetOS in scope, IOSTarget.TYPE, skipInstall = true, scope),
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

  private def deviceTask(scope:Scope) = Def.task[Unit]{
    val log = streams.value.log
    val (config, compiler) = buildTask(configIOSTask(configTask(robovmTargetArch in device, robovmTargetOS in scope, IOSTarget.TYPE, skipInstall = true, scope), ThisScope.in(device.key))).value

    val launchParameters = config.getTarget.createLaunchParameters()

    launchParameters match {
      case iLP: IOSDeviceLaunchParameters =>
        val prefDevices = robovmPreferredDevices.value
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

 lazy val iOSProjectSettings = Seq(
    robovmSimulatorDevice := None,
    robovmProvisioningProfile := None,
    robovmSigningIdentity := None,
    robovmPreferredDevices := Nil,
    lastUsedDeviceFile := target.value / "LastUsediOSDevice.txt",

    device := deviceTask(ThisScope.in(device.key)).value,
    device in Debug := deviceTask(ThisScope.in(device.key).in(Debug)).value,
    robovmTargetArch in device := Array(if((robovmTarget64bit in device).value) Arch.arm64 else Arch.thumbv7),

    //TODO Allow specifying SDK version and device version in simulator tasks?
    iphoneSim := simulatorTask(ThisScope.in(iphoneSim.key), DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPhone)).value,
    iphoneSim in Debug := simulatorTask(ThisScope.in(iphoneSim.key).in(Debug), DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPhone)).value,
    ipadSim := simulatorTask(ThisScope.in(ipadSim.key), DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPad)).value,
    ipadSim in Debug := simulatorTask(ThisScope.in(ipadSim.key).in(Debug), DeviceType.getBestDeviceType(DeviceType.DeviceFamily.iPad)).value,
    simulator := simulatorTask(ThisScope.in(simulator.key), null).value,
    simulator in Debug := simulatorTask(ThisScope.in(simulator.key).in(Debug), null).value,
    robovmTargetArch in simulator := Array(if((robovmTarget64bit in simulator).value) Arch.x86_64 else Arch.x86),

    robovmTarget64bit := false,

    ipa := {
      val (_, compiler) = buildTask(configIOSTask(configTask(robovmTargetArch in ipa, robovmTargetOS in ipa, IOSTarget.TYPE, skipInstall = false, ThisScope.in(ipa.key)), ThisScope.in(ipa.key))).value
      compiler.archive()
    },
   robovmTargetOS := Some(OS.ios),
   robovmTargetArch in ipa := Array(Arch.thumbv7, Arch.arm64),
    simulatorDevices := {
      val devices = DeviceType.getSimpleDeviceTypeIds
      for (simpleDevice <- scala.collection.convert.wrapAsScala.iterableAsScalaIterable(devices)) {
        println(simpleDevice)
      }
      println(devices.size() + " devices found.")
    }
  )
  //endregion

  //region Native

  private def nativeTask(scope:Scope, buildOnly:Boolean) = Def.task[Unit]{
    val log = streams.value.log

    val (config, compiler) = buildTask(configTask(robovmTargetArch in scope, robovmTargetOS in scope, ConsoleTarget.TYPE, skipInstall = true, scope)).value

    if(config.getOs == null){
      log.error("Native compiling is not supported on this platform")
    }else if(buildOnly){
      compiler.install()
      log.debug("nativeBuild task finished")
    }else{
      val launchParameters = config.getTarget.createLaunchParameters()
      val code = compiler.launch(launchParameters)
      log.debug("native task finished (exit code "+code+")")
    }
  }


  lazy val nativeProjectSettings = Seq(
    robovmTargetArch := {
      try {
        Array(Arch.getDefaultArch)
      }catch{
        case _:Throwable =>
          //getDefaultArch May throw java.lang.UnsatisfiedLinkError, which is not caught by scala.util.Try
          Array.empty[Arch]
      }
    },
    robovmTargetOS := {
      try {
        Some(OS.getDefaultOS)
      }catch{
        case _:Throwable =>
          //getDefaultOS May throw java.lang.UnsatisfiedLinkError, which is not caught by scala.util.Try
          None
      }
    },
    native := nativeTask(ThisScope.in(native.key), buildOnly = false).value,
    native in Debug := nativeTask(ThisScope.in(native.key).in(Debug), buildOnly = false).value,
    nativeBuild := nativeTask(ThisScope.in(native.key), buildOnly = true).value,
    nativeBuild in Debug := nativeTask(ThisScope.in(native.key).in(Debug), buildOnly = true).value
  )
    
  //endregion
}
