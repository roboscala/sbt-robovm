package sbtrobovm.interfacebuilder

import java.io.File
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
import scala.collection.mutable

object RobovmInterfaceBuilder {

  private val integratorProxies = new mutable.HashMap[ModuleID, Option[IBIntegratorProxy]]()

  lazy val ibIntegrationSettings = Seq(
    robovmIBDirectory := {
      val result = target.value / "robovm-xcode"
      IO.createDirectory(result)
      result
    },
    robovmIBIntegrator := {
      val log = (streams in robovmIBIntegrator).value.log
      /*
      This is not very sbt-like, but probably the easiest approach.

      Because this needs to depend on several tasks (all of which are tasks because `streams` is a task for some reason),
      this has to be a task. That means that IBIntegrator would be created multiple times, but that must not happen.

      So this lazily creates IBIntegratorProxy and caches it for future invocations.

      When created, adds a shutdown hook to shutdown the proxy's change detection threads.
       */
      val project = (projectID in robovmIBIntegrator).value
      if(!integratorProxies.contains(project)){
        val home = robovmHome.value
        val compilerLogger = robovmCompilerLogger.value
        val projectName = (name in robovmIBIntegrator).value
        val robovmIBFolder = robovmIBDirectory.value
        val result = new IBIntegratorProxy(home, compilerLogger, projectName, robovmIBFolder, project.toString())
        log.debug("Created IBIntegrator proxy (robovmHome="+home.getBinDir.getCanonicalPath+", projectName="+projectName+", ibFolder="+robovmIBFolder.getCanonicalPath+") valid="+result.isValid)

        if(result.isValid){
          result.start()

          java.lang.Runtime.getRuntime.addShutdownHook(new Thread("IBIntegrator Shutdown Hook"){
            override def run(): Unit = {
              result.shutDown()
            }
          })
          integratorProxies(project) = Some(result)
        }else{
          integratorProxies(project) = None
        }
      }

      val integratorProxyOpt = integratorProxies(project)
      integratorProxyOpt.collect {
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

          log.debug("Updated integrator proxy ("+result+") with:\n\tclasspath: "+classpath+"\n\tsourceFolders: "+sourceFolders+"\n\tresourceFolders: "+resourceFolders)

          val infoPList = configuration.getInfoPList
          if(infoPList != null && infoPList.getFile.isFile){
            result.setInfoPlist(infoPList.getFile)
            log.debug("... and PList set to "+infoPList.getFile.getCanonicalPath)
          }else log.debug("... and PList not set to anything")

      }

      integratorProxyOpt
    },
    robovmIBScope := ThisScope,
    commands += RobovmInterfaceBuilder.interfaceBuilderCommand,
    commands ++= Seq(
      createCommand("createStoryboard", "Storyboard", "storyboard", (proxy, directory, name) => {proxy.newIOSStoryboard(name, directory)}),
      createCommand("createView", "View", "xib", (proxy, directory, name) => {proxy.newIOSView(name, directory)}),
      createCommand("createViewController", "ViewController", "xib", (proxy, directory, name) => {proxy.newIOSViewController(name, directory)})
    )
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

  private var integratorProxyCache:Option[IBIntegratorProxy] = null

  /**
   * @param quick - used in completions, where state is most likely not going to change and even if, it is not a big deal,
   *              but the speed is crucial
   */
  private def integrator(state:State, quick:Boolean = false):Option[IBIntegratorProxy] = {
    if(quick && integratorProxyCache != null)return integratorProxyCache
    integratorProxyCache = null //Invalidate cache automatically on standard run

    val extracted = Project.extract(state)
    val scope = extracted.get(robovmIBScope)
    Project.runTask(robovmIBIntegrator in scope, state) match {
      case Some((resultState, Value(Some(result)))) =>
        state.log.debug("Retrieved robovmIBIntegrator with scope \""+scope+"\": "+result+" ("+integratorProxies.size+" proxies loaded)")
        val some = Some(result)
        integratorProxyCache = some
        some
      case Some((resultState, Value(None))) =>
        resultState.log.error("interfaceBuilder not supported on this platform or in this distribution")
        None
      case Some((resultState, Inc(cause))) =>
        resultState.log.error("failed to retrieve interfaceBuilder integrator, possibly an error: "+cause)
        None
      case None =>
        state.log.error("interfaceBuilder not defined for robovmIBScope, is it and iOS project?")
        None
    }
  }

  private lazy val interfaceBuilderCommand = Command.command("interfaceBuilder"){originalState =>
    var state = originalState
    val extracted = Project.extract(state)
    val scope = extracted.get(robovmIBScope)

    integrator(state) match {
      case None =>
        state //already failed
      case Some(firstIntegrator) =>
        firstIntegrator.openProject()

        /**
         * Watches for external (std in) input.
         * Submitting empty line exits the loop.
         * Submitting valid sbt command (= whatever can be entered into the sbt console) executes the command.
         * Submitting invalid command shows help
         *
         * @return true if the watch loop should terminate
         */
        def shouldTerminate: Boolean = {
          //See sbt source, BasicCommands:160 - shell
          val history = (state get BasicKeys.historyPath) getOrElse Some(new File(state.baseDir, ".history"))
          val reader = new FullReader(history, state.combinedParser)
          val line = reader.readLine("interfaceBuilder > ")
          line match {
            case None =>
              false //No input
            case Some("") =>
              true //Terminate in this case, Enter was most likely pressed
            case Some("exit") =>
              //Exit has a special handling, because sbt will exit only after this ended,
              // so it would seem like it didn't exit at all

              //Enqueue real exit command
              state = state.::("exit")
              true //And terminate this command
            case Some("interfaceBuilder") =>
              //That would be rather silly (but would work)
              println("Already in the interfaceBuilder mode")
              false
            case Some(command) =>
              parse(command, Command.combine(state.definedCommands)(state)) match {
                case Right(s) =>
                  state = s() // apply found command
                case Left(errMsg) =>
                  println("Unrecognized command \""+command+"\"")
                  println("Enter empty line to terminate interfaceBuilder")
              }
              false
          }
        }

        withAttribute(state, Watched.Configuration, "Continuous execution not configured.") { w =>
          var watchResult = SourceModificationWatch.watch(w.watchPaths(state), w.pollInterval, WatchState.empty)(shouldTerminate)
          var integratorDefined = true
          while(integratorDefined && watchResult._1){
            integrator(state) match { //This will recompile and update the XCode project (if all goes well)
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

  import sbt.complete.DefaultParsers._

  private def createCommand(name:String, whatIsCreated:String, extension:String, action:(IBIntegratorProxy, File,String) => Unit):Command =
    Command[String](name, (name, "Creates a "+whatIsCreated+" on given path"), "detailed help todo")(state => {
      integrator(state, quick = true) match {
          case Some(integrator) =>
            val resourceFolders = integrator.getResourceFolders
            if(resourceFolders.isEmpty){
              state.log.warn("No resource folders defined, define some in robovm.xml")//Because failure() don't seem to show its message
              failure("No resource folders defined, define some in robovm.xml", definitive = true)
            }else{
              //Construct a file parser, with completions to all defined resourceFolders, relative to current-project base dir
              val basePath = file(Project.extract(state).currentRef.build.getPath)
              OptSpace ~> StringBasic
                .examples(new ResourceFileExamples(basePath, integrator.getResourceFolders.asScala.toSet[File], (whatIsCreated, extension)))
            }
          case None =>
            state.log.warn("interfaceBuilder not available")//Because failure() don't seem to show its message
            failure("interfaceBuilder not available", definitive = true)
        }
      })((state, selectedPath) => {
        val selectedFile = new File(selectedPath)
        //Assume that the file is a file that we want to make
        integrator(state).collect{
          case integrator =>
            var directory:File = null
            var name:String = null
            if(selectedPath.endsWith("/")){
              if(selectedFile.exists() && !selectedFile.isDirectory){
                state.log.error("Directory already exists as a file")
              }else{
                directory = selectedFile
                name = whatIsCreated
              }
            }else if(selectedFile.isDirectory){
              directory = selectedFile
              name = whatIsCreated
            }else{
              //File is specified
              directory = selectedFile.getParentFile
              name = selectedFile.getName
            }

            if(directory != null && name != null){
              IO.createDirectory(directory)
              action(integrator, directory, name.stripSuffix("."+extension))
            }
        }
        state
      })
}
