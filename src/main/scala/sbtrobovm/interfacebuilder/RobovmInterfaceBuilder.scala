package sbtrobovm.interfacebuilder

import java.util

import org.robovm.compiler.config.OS
import org.robovm.compiler.target.ios.IOSTarget
import sbt.CommandUtil._
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
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
            if(f.data.isDirectory){
              classpath.add(f.data)
              sourceFolders.add(f.data)
            }
          })

          result.setClasspath(classpath)
          result.setSourceFolders(sourceFolders)

          val resourceFolders = new util.HashSet[File]()
          configuration.getResources.asScala.foreach(r => {
            val dir = r.getDirectory
            if(dir.isDirectory){
              resourceFolders.add(dir)
            }
          })
          result.setResourceFolders(resourceFolders)


          val infoPList = configuration.getInfoPList
          if(infoPList != null && infoPList.getFile.isFile) result.setInfoPlist(infoPList.getFile)
      }

      integratorProxy
    },
    robovmIBScope := ThisScope,
    commands += RobovmInterfaceBuilder.interfaceBuilderCommand
  )

  /**
   * @return Trimmed line of entered input or null if no input available
   */
  private def readStdInput():String = {
    if(System.in.available() <= 0)return null
    //Read a line of input
    val commandSB = new java.lang.StringBuilder
    var char:Int = 0
    do {
      char = System.in.read()
      commandSB.appendCodePoint(char)
    } while(System.in.available() > 0 && !Watched.isEnter(char))
    commandSB.toString.trim
  }

  private lazy val interfaceBuilderCommand = Command.command("interfaceBuilder"){originalState =>
    var state = originalState
    val extracted = Project.extract(state)
    val scope = extracted.get(robovmIBScope)

    def prompt() = {print("interfaceBuilder > ")}

    def integrator():Option[IBIntegratorProxy] = {
      Project.runTask(robovmIBIntegrator in scope, state) match {
        case Some((resultState, Value(Some(result)))) =>
          state = resultState
          Some(result)
        case Some((resultState, Value(None))) =>
          resultState.log.error("interfaceBuilder not supported on this platform or in this distribution")
          state = resultState.fail
          None
        case Some((resultState, Inc(cause))) =>
          resultState.log.error("failed to retrieve interfaceBuilder integrator, possibly an error: "+cause)
          state = resultState.fail
          None
        case None =>
          state.log.error("interfaceBuilder not defined for robovmIBScope, is it and iOS project?")
          state = state.fail
          None
      }
    }

    integrator() match {
      case None =>
        state //already failed
      case Some(firstIntegrator) =>
        firstIntegrator.openProject()
        prompt()

        /**
         * Watches for external (std in) input.
         * Submitting empty line exits the loop.
         * Submitting valid sbt command (= whatever can be entered into the sbt console) executes the command.
         * Submitting invalid command shows help
         *
         * @return true if the watch loop should terminate
         */
        def shouldTerminate: Boolean = {
          readStdInput() match {
            case null =>
              false //No input
            case "" =>
              true //Terminate in this case, Enter was most likely pressed
            case "exit" =>
              //Exit has a special handling, because sbt will exit only after this ended,
              // so it would seem like it didn't exit at all

              //Enqueue real exit command
              state = state.::("exit")
              true //And terminate this command
            case "interfaceBuilder" =>
              //That would be rather silly (but would work)
              println("Already in the interfaceBuilder mode")
              prompt()
              false
            case command =>
              val parser = Command.combine(state.definedCommands)
              parse(command, parser(state)) match {
                case Right(s) =>
                  state = s() // apply found command
                case Left(errMsg) =>
                  println("Unrecognized command \""+command+"\"")
                  println("Enter empty line to terminate interfaceBuilder")
              }
              prompt()
              false
          }
        }

        withAttribute(state, Watched.Configuration, "Continuous execution not configured.") { w =>
          var watchResult = SourceModificationWatch.watch(w.watchPaths(state), w.pollInterval, WatchState.empty)(shouldTerminate)
          var integratorDefined = true
          while(integratorDefined && watchResult._1){
            integrator() match { //This will recompile and update the XCode project (if all goes well)
              case Some(_) =>
                watchResult = SourceModificationWatch.watch(w.watchPaths(state), w.pollInterval, watchResult._2)(shouldTerminate)
              case None =>
                state.log.error("robovmIBIntegrator not defined in scope \""+scope+"\"")
                state = state.fail
                integratorDefined = false
            }
          }
          state
        } //end continuous integration
    }
  }
}
