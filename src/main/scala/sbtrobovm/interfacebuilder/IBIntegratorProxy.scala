package sbtrobovm.interfacebuilder

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.Collections

import org.robovm.compiler.config.Config.Home
import org.robovm.compiler.log.Logger

import scala.util.Try
import scala.util.control.NonFatal


/**
 * Reflection proxy for safe handling of IBIntegrator, class bundled into the RoboVM distribution, which is internal
 * and can't be compiled against.
 */
class IBIntegratorProxy private (private val log:Logger, override val toString:String) {
  private var instance:Object = null
  private var failureReason:String = "IBIntegratorProxy is valid"

  def this(home:Home, logger:Logger, name:String, xcodeDirectory:File, debugName:String){
    this(logger, debugName)
    IBIntegratorProxy.instantiate(home, logger, name, xcodeDirectory) match {
      case Right(integrator) =>
        instance = integrator
      case Left(reasonFailure) =>
        failureReason = reasonFailure
    }
  }

  def isValid:Boolean = instance != null

  def invalidityReason:String = failureReason

  private def invoke[T <: Object](method:String, types:Class[_]*)(parameters:Object*):T = {
    //println("Invoking "+method+"("+parameters.mkString(", ")+")")
    def err(msg:String, throwable: Throwable = null):T = {
      System.err.println("IBIntegratorProxy: "+msg)
      if(throwable != null)throwable.printStackTrace(System.err)
      null.asInstanceOf[T]
    }

    if(!isValid)return err("Instance not valid")
    try {
      IBIntegratorProxy.integratorClass.getMethod(method, types:_*).invoke(instance, parameters:_*).asInstanceOf[T]
    }catch{
      case nsm:NoSuchMethodException =>
        err("Method "+method+"("+types.map(_.getSimpleName).mkString(", ")+") does not exist")
      case ite:InvocationTargetException =>
        err("Method "+method+"("+types.map(_.getSimpleName).mkString(", ")+") has thrown an exception", ite.getCause)
      case NonFatal(e) =>
        err("Failed to invoke "+method+"("+types.map(_.getSimpleName).mkString(", ")+")", e)
    }
  }

  def setInfoPlist(plist:File): Unit = {
    invoke("setInfoPlist", classOf[File])(plist)
  }

  private var resourceFolders:java.util.Set[File] = Collections.emptySet[File]()

  def setResourceFolders(resourceFolders:java.util.Set[File]): Unit ={
    this.resourceFolders = resourceFolders
    invoke("setResourceFolders",classOf[java.util.Set[File]])(resourceFolders)
  }

  def getResourceFolders = resourceFolders

  def setClasspath(classpath:java.util.List[File]): Unit = {
    invoke("setClasspath",classOf[java.util.List[File]])(classpath)
  }

  def setSourceFolders(sourceFolders:java.util.Set[File]): Unit ={
    invoke("setSourceFolders",classOf[java.util.Set[File]])(sourceFolders)
  }

  def newIOSStoryboard(name:String, path:File): Unit ={
    invoke("newIOSStoryboard", classOf[String], classOf[File])(name, path)
  }

  def newIOSView(name:String, path:File): Unit ={
    invoke("newIOSView", classOf[String], classOf[File])(name, path)
  }

  def newIOSViewController(name:String, path:File): Unit ={
    invoke("newIOSViewController", classOf[String], classOf[File])(name, path)
  }

  def openProject(): Unit ={
    invoke("openProject")()
  }

  def openProjectFile(file:String): Unit ={
    invoke("openProjectFile", classOf[String])(file)
  }

  def start(): Unit ={
    invoke("start")()
  }

  def shutDown(): Unit ={
    invoke("shutDown")()
  }

}

object IBIntegratorProxy {
  private val integratorClass:Class[_] = Try(Class.forName("com.robovm.ibintegrator.IBIntegrator")).getOrElse(null)

  import java.io.File

  import org.robovm.compiler.config.Config.Home
  import org.robovm.compiler.log.Logger

  private def instantiate(home:Home, logger:Logger, name:String, xcodeDirectory:File): Either[String, Object] = {
    if(integratorClass == null) return Left("IBIntegrator class not found")
    if(!System.getProperty("os.name").toLowerCase.contains("mac os x")) return Left("IB integration is available only on OS X")

    try{
      Right(integratorClass.getConstructor(classOf[Home], classOf[Logger], classOf[String], classOf[File]).newInstance(home, logger, name, xcodeDirectory).asInstanceOf[Object])
    } catch {
      case ite: InvocationTargetException =>
        val t = ite.getCause
        logger.debug("IBIntegrationProxy: Failed to instantiate IBIntegrator: "+t)
        if(t.getClass.getName == "com.robovm.lm.UnlicensedException"){
          Left(t.getMessage)
        }else{
          Left("Failed to instantiate IBIntegrator: "+t)
        }
      case t: Throwable =>
        logger.debug("IBIntegrationProxy: Failed to instantiate IBIntegrator: "+t)
        Left("Failed to instantiate IBIntegrator: "+t)
    }
  }

}
