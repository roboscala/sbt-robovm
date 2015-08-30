package sbtrobovm.interfacebuilder

import java.io.File

import org.robovm.compiler.config.Config.Home
import org.robovm.compiler.log.Logger

import scala.util.{Failure, Try}


/**
 * Reflection proxy for safe handling of IBIntegrator, class bundled into the RoboVM distribution, which is internal
 * and can't be compiled against.
 */
class IBIntegratorProxy private (private val log:Logger) {
  private var instance:Object = _

  def this(home:Home, logger:Logger, name:String, xcodeDirectory:File){
    this(logger)
    instance = IBIntegratorProxy.instantiate(home, logger, name, xcodeDirectory)
  }

  def isValid:Boolean = instance != null

  private def invokeExplicit[T <: Object](method:String, types:Class[_]*)(parameters:Object*):T = {
    if(!isValid)return null.asInstanceOf[T]
    //println("Invoking "+method+"("+parameters.mkString(", ")+")")
    val result = Try(IBIntegratorProxy.integratorClass.getMethod(method, types:_*).invoke(instance, parameters:_*))
    if(result.isFailure){
      System.err.println("IBIntegratorProxy: Failed to invoke method "+method+":")
      result.asInstanceOf[Failure[_]].exception.printStackTrace(System.err)
    }
    result.getOrElse(null).asInstanceOf[T]
  }

  private def invoke[T <: Object](method:String, parameters:Object*):T = {
    if(!isValid)return null.asInstanceOf[T]
    //println("Invoking "+method+"("+parameters.mkString(", ")+")")
    val result = Try(IBIntegratorProxy.integratorClass.getMethod(method, parameters.map(_.getClass):_*).invoke(instance, parameters:_*))
    if(result.isFailure){
      System.err.println("IBIntegratorProxy: Failed to invoke method "+method+":")
      result.asInstanceOf[Failure[_]].exception.printStackTrace(System.err)
    }
    result.getOrElse(null).asInstanceOf[T]
  }

  def setInfoPlist(plist:File): Unit = {
    invoke("setInfoPlist", plist)
  }

  def setResourceFolders(resourceFolders:java.util.Set[File]): Unit ={
    invokeExplicit("setResourceFolders",classOf[java.util.Set[File]])(resourceFolders)
  }

  def setClasspath(classpath:java.util.List[File]): Unit = {
    invokeExplicit("setClasspath",classOf[java.util.List[File]])(classpath)
  }

  def setSourceFolders(sourceFolders:java.util.Set[File]): Unit ={
    invokeExplicit("setSourceFolders",classOf[java.util.Set[File]])(sourceFolders)
  }

  def newIOSStoryboard(name:String, path:File): Unit ={
    invoke("newIOSStoryboard", name, path)
  }

  def newIOSView(name:String, path:File): Unit ={
    invoke("newIOSView", name, path)
  }

  def newIOSWindow(name:String, path:File): Unit ={
    invoke("newIOSWindow", name, path)
  }

  def openProject(): Unit ={
    invoke("openProject")
  }

  def openProjectFile(file:String): Unit ={
    invoke("openProjectFile",file)
  }

  def start(): Unit ={
    invoke("start")
  }

  def shutDown(): Unit ={
    invoke("shutDown")
  }

}

object IBIntegratorProxy {
  private val integratorClass:Class[_] = Try(Class.forName("com.robovm.ibintegrator.IBIntegrator")).getOrElse(null)

  import java.io.File
  import org.robovm.compiler.config.Config.Home
  import org.robovm.compiler.log.Logger

  private def instantiate(home:Home, logger:Logger, name:String, xcodeDirectory:File): Object ={
    if(!isAvailable)return null
    try{
      integratorClass.getConstructor(classOf[Home], classOf[Logger], classOf[String], classOf[File]).newInstance(home, logger, name, xcodeDirectory).asInstanceOf[Object]
    } catch {
      case t: Throwable =>
        logger.error("IBIntegrationProxy: Failed to instantiate IBIntegrator: "+t)
        null
    }
  }

  private def isAvailable: Boolean = {
    if(integratorClass == null || !System.getProperty("os.name").toLowerCase.contains("mac os x"))return false
    Try(integratorClass.getDeclaredMethod("isAvailable").invoke(null).asInstanceOf[Boolean]).getOrElse(false)
  }

}
