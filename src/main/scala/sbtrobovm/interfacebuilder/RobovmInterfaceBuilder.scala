package sbtrobovm.interfacebuilder

import java.io.File
import java.util

import org.robovm.compiler.config.OS
import org.robovm.compiler.target.ios.IOSTarget
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbtrobovm.RobovmPlugin._
import sbtrobovm.RobovmProjects

import scala.collection.JavaConverters._
import scala.collection.mutable

object RobovmInterfaceBuilder {

  private val integratorProxies = new mutable.HashMap[ModuleID, Either[String, IBIntegratorProxy]]()

  lazy val ibIntegrationSettings = Seq(
    robovmIBDirectory := {
      val result = target.value / "robovm-xcode"
      IO.createDirectory(result)
      result
    },
    /**
     * Beware: This may fail when any dependent tasks fail.
     * However, that does not necessarily mean that robovmIBIntegrator is not available.
     */
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
          integratorProxies(project) = Right(result)
        }else{
          integratorProxies(project) = Left(result.invalidityReason)
        }
      }

      val integratorProxyEither = integratorProxies(project)
      integratorProxyEither match {
        case Right(ibProxy) =>
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

          ibProxy.setClasspath(classpath)
          ibProxy.setSourceFolders(sourceFolders)

          val resourceFolders = new util.HashSet[File]()
          configuration.getResources.asScala.foreach(r => {
            val dir = r.getDirectory
            if(dir.isDirectory){
              resourceFolders.add(dir)
            }
          })
          ibProxy.setResourceFolders(resourceFolders)

          log.debug("Updated integrator proxy ("+ibProxy+") with:\n\tclasspath: "+classpath+"\n\tsourceFolders: "+sourceFolders+"\n\tresourceFolders: "+resourceFolders)

          val infoPList = configuration.getInfoPList
          if(infoPList != null && infoPList.getFile.isFile){
            ibProxy.setInfoPlist(infoPList.getFile)
            log.debug("... and PList set to "+infoPList.getFile.getCanonicalPath)
          }else log.debug("... and PList not set to anything")
        case _ =>
      }

      integratorProxyEither
    },
    robovmIBScope := ThisScope,
    commands += RobovmInterfaceBuilder.interfaceBuilderCommand,
    commands ++= Seq(
      createCommand("createStoryboard", "Storyboard", "storyboard", (proxy, directory, name) => {proxy.newIOSStoryboard(name, directory)}),
      createCommand("createView", "View", "xib", (proxy, directory, name) => {proxy.newIOSView(name, directory)}),
      createCommand("createViewController", "ViewController", "xib", (proxy, directory, name) => {proxy.newIOSViewController(name, directory)})
    )
  )

  def clearIBProxyCreationFailures(): Unit ={
    integratorProxies --= integratorProxies.filter(pair => pair._2.isLeft).keys
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
      case Some((resultState, Value(Right(result)))) =>
        state.log.debug("Retrieved robovmIBIntegrator with scope \""+scope+"\": "+result+" ("+integratorProxies.size+" proxies loaded)")
        val some = Some(result)
        integratorProxyCache = some
        some
      case Some((resultState, Value(Left(reason)))) =>
        resultState.log.error("interfaceBuilder not available: "+reason)
        None
      case Some((resultState, Inc(cause))) =>
        //This in most cases mean that some of the dependant tasks failed.
        //Usually when user source code is invalid and fails to compile.
        resultState.log.warn("Failed to retrieve interfaceBuilder integrator")
        resultState.log.debug("Caused by: "+cause)
        None
      case None =>
        state.log.error("interfaceBuilder not defined for robovmIBScope, is it and iOS project?")
        None
    }
  }

  /**
   * Thread which watches the sources and recompiles the RoboVM project each time they change.
   * Used state is not changed by this thread, so it is not returned back.
   */
  private class InterfaceBuilderCommandCompileLoop(state: =>State, shouldTerminate: =>Boolean) extends Thread("interfaceBuilder CompileLoop") {

    val CommandLock = new Object

    setDaemon(true)

    val PollInterval = 500 //ms

    override def run(): Unit = {
      Project.runTask(unmanagedSources in (Compile, robovmIBScope),state) match {
        case Some((_, Value(watchedPaths))) =>
          state.log.debug("Watched paths:\n"+watchedPaths.map(_.getCanonicalPath).sorted.mkString("\n"))

          var watchResult = SourceModificationWatch.watch(watchedPaths, PollInterval, WatchState.empty)(shouldTerminate)
          while(watchResult._1){
            //integrator() will return the integrator (which we are not interested in) and recompile/update
            // the project for XCode (which we want)
            //When None is returned, that probably means that user has errors in the code.
            //Info about that has been already logged by integrator() method, so ignore it.
            //(It is very unlikely that this happened because of suddenly broken state, and even if so, we don't care)
            state.log.debug("Will reload interfaceBuilder")
            CommandLock.synchronized(integrator(state))
            state.log.debug("interfaceBuilder reloaded")
            watchResult = SourceModificationWatch.watch(watchedPaths, PollInterval, watchResult._2)(shouldTerminate)
          }
        case cause =>
          state.log.error("Failed to retrieve paths to watch, interfaceBuilder won't update. (runTask returned: "+cause+")")

      }
      state.log.debug("interfaceBuilder daemon stopped")
    }
  }

  private lazy val interfaceBuilderCommand = Command.command("interfaceBuilder"){originalState =>
    var state = originalState

    integrator(state) match {
      case None =>
        state.log.debug("robovmIBIntegrator retrieved in scope \""+Project.extract(state).get(robovmIBScope)+"\"")
        state //already failed
      case Some(firstIntegrator) =>
        firstIntegrator.openProject()

        var terminateIBCommand = false
        val compileLoop = new InterfaceBuilderCommandCompileLoop(state, terminateIBCommand)
        compileLoop.start()

        /*
         * Watches for user (std in) input using sbt's methods (supports tab completion etc.)
         * Submitting empty line exits the loop.
         * Submitting valid sbt command (= whatever can be entered into the sbt console) executes the command.
         * Submitting invalid command shows help
         *
         * Executing commands in both here and in the compile loop thread is synchronized on compile loop's CommandLock
         * (This locking may not be necessary, but better safe than sorry)
         */
        while(!terminateIBCommand){
          //See sbt source, BasicCommands:160 - shell
          val history = (state get BasicKeys.historyPath) getOrElse Some(new File(state.baseDir, ".history"))
          val reader = new FullReader(history, state.combinedParser)
          val line = reader.readLine("interfaceBuilder > ")
          line match {
            case None =>
              terminateIBCommand = true //Input is broken, terminate
            case Some("") =>
              terminateIBCommand = true //Terminate in this case, Enter was most likely pressed
            case Some("exit") =>
              //Exit has a special handling, because sbt will exit only after this ended,
              // so it would seem like it didn't exit at all

              //Enqueue real exit command
              state = state.::("exit")
              terminateIBCommand = true //And terminate this command
            case Some("interfaceBuilder") =>
              //That would be rather silly (but would work)
              println("Already in the interfaceBuilder mode")
            case Some(command) =>
              parse(command, Command.combine(state.definedCommands)(state)) match {
                case Right(s) =>
                  state = compileLoop.CommandLock.synchronized(s()) // apply found command
                case Left(errMsg) =>
                  println("Unrecognized command \""+command+"\"")
                  println("Enter empty line to terminate interfaceBuilder")
              }
          }
        }

        //Command terminated, make sure that the compile loop terminates as well
        state.log.debug("Waiting for compile loop to finish...")
        compileLoop.join(15000) //It should stop safely in that time
        if(compileLoop.isAlive){
          state.log.warn("Compile loop didn't finish in time")
        }

        state
        //end of interfaceBuilder inner workings
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
