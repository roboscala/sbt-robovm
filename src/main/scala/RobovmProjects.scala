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
import org.robovm.compiler.log.Logger
import org.robovm.compiler.target.ios.IOSSimulatorLaunchParameters
import org.robovm.compiler.target.ios.IOSTarget

object RobovmProjects {
  object Standard {
    def launchTask(arch: Arch, launcher: Config => Unit) = (executableName, frameworks, nativePath, fullClasspath in Compile, mainClass in run in Compile, streams) map {
      (n, f, np, cp, mc, st) => {
        val robovmLogger = new Logger() {
          def debug(s: String, o: java.lang.Object*) = st.log.debug(s.format(o:_*))
          def info(s: String, o: java.lang.Object*) = st.log.info(s.format(o:_*))
          def warn(s: String, o: java.lang.Object*) = st.log.warn(s.format(o:_*))
          def error(s: String, o: java.lang.Object*) = st.log.error(s.format(o:_*))
        }

        val builder = new Config.Builder()

        builder.mainClass(mc.getOrElse("Main"))
          .executableName(n)
          .logger(robovmLogger)
          .skipInstall(true)
          .targetType(TargetType.ios)
          .os(OS.ios)
          .arch(arch)

        f foreach { framework => builder.addFramework(framework) }
        np.listFiles foreach { lib => builder.addLib(lib.getPath()) }
        cp.map(i => i.data) foreach { file => builder.addClasspathEntry(file) }

        val config = builder.build()
        val compiler = new AppCompiler(config)
        compiler.compile()

        launcher(config)
      }
    }

    private val deviceTask = launchTask(Arch.thumbv7, (config) => {
        val launchParameters = config.getTarget().createLaunchParameters()
        config.getTarget().launch(launchParameters).waitFor()
    })

    private val iphoneSimTask = launchTask(Arch.x86, (config) => {
        val launchParameters = config.getTarget().createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        launchParameters.setFamily(IOSSimulatorLaunchParameters.Family.iphone)
        config.getTarget().launch(launchParameters).waitFor()
    })

    private val ipadSimTask = launchTask(Arch.x86, (config) => {
        val launchParameters = config.getTarget().createLaunchParameters().asInstanceOf[IOSSimulatorLaunchParameters]
        launchParameters.setFamily(IOSSimulatorLaunchParameters.Family.ipad)
        config.getTarget().launch(launchParameters).waitFor()
    })

    private val ipaTask = launchTask(Arch.thumbv7, (config) => {
      // TODO: Add after robovm 0.0.5
      //config.getTarget().asInstanceOf[IOSTarget].createIpa()
    })

    lazy val robovmSettings = Seq(
      libraryDependencies ++= Seq(
        "org.robovm" % "robovm-rt" % "0.0.4",
        "org.robovm" % "robovm-objc" % "0.0.4",
        "org.robovm" % "robovm-cocoatouch" % "0.0.4",
        "org.robovm" % "robovm-cacerts-full" % "0.0.4"
      ),
      executableName := "RobovmApp",
      frameworks := Seq.empty,
      nativePath <<= (baseDirectory) (_ / "lib"),
      device <<= deviceTask dependsOn (compile in Compile),
      iphoneSim <<= iphoneSimTask dependsOn (compile in Compile),
      ipadSim <<= ipadSimTask dependsOn (compile in Compile),
      ipa <<= ipaTask dependsOn (compile in Compile)
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
