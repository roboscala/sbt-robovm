package sbtrobovm

import java.io.{IOException, StringReader}
import java.util

import org.apache.commons.io.FileUtils
import org.jboss.shrinkwrap.resolver.api.SBTRoboVMResolver
import org.robovm.compiler.AppCompiler
import org.robovm.compiler.config.Config.TargetType
import org.robovm.compiler.config.{Arch, Config, OS}
import org.robovm.compiler.log.Logger
import org.robovm.compiler.target.ios._
import sbt.Defaults._
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._

object RobovmProjects {

  def configTask(arch: => Arch, os: => OS, targetType: => TargetType, skipInstall: Boolean) = Def.task[Config.Builder] {
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
    builder.arch(arch)

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

    val enableRobovmDebug = robovmDebug.value
    if(enableRobovmDebug){
      st.log.info("RoboVM Debug is enabled")
      builder.debug(true)
      val debugPort = robovmDebugPort.value
      if(debugPort != -1){
        builder.addPluginArgument("debug:jdwpport=" + debugPort)
      }
    }

    builder
  }

  def buildTask(configBuilderTask:Def.Initialize[Task[Config.Builder]]) = Def.task[(Config, AppCompiler)] {
    val st = streams.value

    val config = configBuilderTask.value.build()
    st.log.info("Compiling RoboVM app, this could take a while")
    val compiler = new AppCompiler(config)
    compiler.compile()

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
    robovmDebugPort := -1,
    robovmHome := new Config.Home(new SBTRoboVMResolver(streams.value.log).resolveAndUnpackRoboVMDistArtifact(RoboVMVersion)),
    robovmInputJars := (fullClasspath in Compile).value map (_.data),
    robovmVerbose := false,
    robovmLicense := {
      com.robovm.lm.LicenseManager.forkUI()
    },
    ivyConfigurations += ManagedNatives
  )

  trait RoboVMProject {

    val projectSettings:Seq[Def.Setting[_]]

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
      coreDefaultSettings ++ baseSettings ++ projectSettings ++ settings,
      configurations
    )
  }

  object iOSProject extends RoboVMProject {

    def configIOSTask(configBuilderTask:Def.Initialize[Task[Config.Builder]], scope: Scoped) = Def.task[Config.Builder]{
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

    def buildSimulatorTask(scope:Scoped) = Def.task[(Config, AppCompiler)]{
      buildTask(
        configIOSTask(
          configTask(
          {
            Arch.x86 //TODO Support for launching 64bit simulator
          }, OS.ios, TargetType.ios, skipInstall = true),
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

    override lazy val projectSettings = Seq(
      robovmSimulatorDevice := None,
      provisioningProfile := None,
      signingIdentity := None,
      device := {
        //TODO Allow launching on 64bit device if specified
        val (config, compiler) = buildTask(configIOSTask(configTask(Arch.thumbv7, OS.ios, TargetType.ios, skipInstall = true), device)).value

        val launchParameters = config.getTarget.createLaunchParameters()
        val code = compiler.launch(launchParameters)
        streams.value.log.debug("device task finished (exit code "+code+")")
      },
      iphoneSim := {
        val (config,compiler) = buildSimulatorTask(iphoneSim).value
        val device = DeviceType.getBestDeviceType(config.getHome, DeviceType.DeviceFamily.iPhone) //TODO Allow specifying SDK version and iPhone version?
        val code = runSimulator(config, compiler, device)
        streams.value.log.debug("iphoneSim task finished (exit code "+code+")")
      },
      ipadSim := {
        val (config,compiler) = buildSimulatorTask(ipadSim).value
        val device = DeviceType.getBestDeviceType(config.getHome, DeviceType.DeviceFamily.iPad) //TODO Allow specifying SDK version and iPad version?
        val code = runSimulator(config, compiler, device)
        streams.value.log.debug("ipadSim task finished (exit code "+code+")")
      },
      ipa := {
        val config = configIOSTask(configTask(arch = null, OS.ios, TargetType.ios, skipInstall = false), ipa).value.build()
        val compiler = new AppCompiler(config)
        val architectures = new util.ArrayList[Arch]()
        architectures.add(Arch.thumbv7)
        architectures.add(Arch.arm64)
        compiler.createIpa(architectures)
      },
      simulator := {
        val (config,compiler) = buildSimulatorTask(simulator).value

        val simulatorDeviceName: String = robovmSimulatorDevice.value.getOrElse(sys.error("Define device kind name first. See robovmSimulatorDevice setting and simulatorDevices task."))
        val device = DeviceType.getDeviceType(config.getHome, simulatorDeviceName)
        if (device == null) sys.error( s"""iOS simulator device "$simulatorDeviceName" not found.""")

        val code = runSimulator(config, compiler, device)
        streams.value.log.debug("simulator task finished (exit code "+code+")")
      },
      simulatorDevices := {
        val devices = DeviceType.getSimpleDeviceTypeIds(robovmHome.value)
        for (simpleDevice <- scala.collection.convert.wrapAsScala.iterableAsScalaIterable(devices)) {
          println(simpleDevice)
        }
        println(devices.size() + " devices found.")
      }
    )

  }

  object NativeProject extends RoboVMProject {

    override lazy val projectSettings = Seq(
      native := {
        val (config, compiler) = buildTask(configTask(Arch.getDefaultArch, OS.getDefaultOS, TargetType.console, skipInstall = true)).value

        val launchParameters = config.getTarget.createLaunchParameters()
        val code = compiler.launch(launchParameters)
        streams.value.log.debug("native task finished (exit code "+code+")")
      }
    )
    
  }
}
