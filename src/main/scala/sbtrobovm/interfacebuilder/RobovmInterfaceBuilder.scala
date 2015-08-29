package sbtrobovm.interfacebuilder

import java.util

import org.robovm.compiler.config.OS
import org.robovm.compiler.target.ios.IOSTarget
import sbt.CommandUtil._
import sbt.Keys._
import sbt._
import sbtrobovm.RobovmPlugin._
import sbtrobovm.RobovmProjects

import scala.collection.JavaConverters._

object RobovmInterfaceBuilder {

  private var integratorProxyInitialized = false
  private var integratorProxy:Option[IBIntegratorProxy] = None

  lazy val ibIntegrationSettings = Seq(
    robovmIBDirectory := {
      val result = target.value / "robovm-xcode"
      IO.createDirectory(result)
      result
    },
    robovmIBIntegrator := {
      /*
      This is not very sbt-like, but probably the easiest approach.

      Because this needs to depend on several tasks (all of which are tasks because `streams` is a task for some reason),
      this has to be a task. That means that IBIntegrator would be created multiple times, but that must not happen.

      So this lazily creates IBIntegratorProxy and caches it for future invocations.

      When created, adds a shutdown hook to shutdown the proxy's change detection threads.
       */
      if(!integratorProxyInitialized){
        integratorProxyInitialized = true

        val projectName = (name in robovmIBIntegrator).value
        val result = new IBIntegratorProxy(robovmHome.value, robovmCompilerLogger.value, projectName, robovmIBDirectory.value)

        integratorProxy = if(result.isValid){
          result.start()

          java.lang.Runtime.getRuntime.addShutdownHook(new Thread("IBIntegrator Shutdown Hook"){
            override def run(): Unit = {
              result.shutDown()
            }
          })
          Some(result)
        }else None
      }

      integratorProxy.collect {
        case result =>
          val configuration = RobovmProjects.configTask(RobovmProjects.ipaArchitectureSetting, OS.ios, IOSTarget.TYPE, skipInstall = true, robovmIBIntegrator.scope).value.build()

          //Not sure what classpath and source folders should be. robovm-idea seems to set it to compile out of the project
          val classpath = new util.ArrayList[File]()
          val sourceFolders = new util.HashSet[File]()

          (exportedProducts in (Compile, robovmIBIntegrator)).value.foreach(f => {
            classpath.add(f.data)
            sourceFolders.add(f.data)
          })

          result.setClasspath(classpath)
          result.setSourceFolders(sourceFolders)

          val resourceFolders = new util.HashSet[File]()
          configuration.getResources.asScala.foreach(r => {
            val dir = r.getDirectory
            resourceFolders.add(dir)
          })
          result.setResourceFolders(resourceFolders)

          result.setInfoPlist(configuration.getInfoPList.getFile)
      }

      integratorProxy
    },
    robovmIBStart := {
      robovmIBIntegrator.value match {
        case Some(_) =>
          println("Interface Builder Daemon is running")
        case None =>
          println("Interface Builde Daemon could not be started")
      }
    },
    robovmIBOpen := {
      robovmIBIntegrator.value.collect {
        case integrator =>
          integrator.openProject()
      }
    },
    robovmIBScope := ThisScope,
    commands += RobovmInterfaceBuilder.interfaceBuilderCommand
  )

  lazy val interfaceBuilderCommand = Command.command("interfaceBuilder"){state =>
    val extracted = Project.extract(state)
    val scope = extracted.get(robovmIBScope)

    if(Project.runTask(robovmIBOpen in scope, state).isEmpty){
      //Not defined in this scope, what now?
      println("Not defined in this scope! EXPLOSION IMMINENT")
    }
    withAttribute(state, Watched.Configuration, "Continuous execution not configured.") { w =>

      def shouldTerminate: Boolean = {
        if(System.in.available() <= 0) false
        else {
          val commandSB = new java.lang.StringBuilder
          while(System.in.available() > 0){
            commandSB.appendCodePoint(System.in.read())
          }
          val command = commandSB.toString.trim
          if(command.isEmpty){
            true //Terminate in this case, Enter was most likely pressed
          } else {
            command match {
              case "device" =>
                extracted.runTask(device in scope, state)
              case "simulator" =>
                extracted.runTask(simulator in scope, state)
              case "iphoneSim" =>
                extracted.runTask(iphoneSim in scope, state)
              case "ipadSim" =>
                extracted.runTask(ipadSim in scope, state)
              case unrecognized =>
                println("Unrecognized interfaceBuilder command \""+unrecognized+"\"")
                println("Available commands: device, simulator, iphoneSim, ipadSim\n   or empty line to terminate")
            }
            false
          }
        }
      }

      var watchResult = SourceModificationWatch.watch(w.watchPaths(state), w.pollInterval, WatchState.empty)(shouldTerminate)
      while(watchResult._1){
        Project.runTask(robovmIBIntegrator in scope, state) //This will recompile and update the XCode project
        watchResult = SourceModificationWatch.watch(w.watchPaths(state), w.pollInterval, watchResult._2)(shouldTerminate)
      }
      state
    }
  }
}
