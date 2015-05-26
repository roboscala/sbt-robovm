package sbtrobovm

import org.apache.commons.io.FileUtils
import sbt.Keys._
import sbt.{Def, _}

import scala.collection.mutable.ArrayBuffer
import scala.xml.Elem

/**
 * Do not instantiate. Use RobovmPlugin.
 *
 * User: Darkyen
 * Date: 29/03/15
 * Time: 12:47
 */
trait RobovmUtils {

  val ManagedNatives = config("ManagedNatives")

  /**
   * Creates task that produces native library files from dependencies that have RoboVMNativesConfiguration.
   * Whether or not a file is indeed a native library is decided by checking the extension.
   *
   * Found .jar files are extracted and searched recursively.
   *
   * NOTE: Since 1.2.0 RoboVM can detect and use natives from classpath, if properly configured.
   * See https://github.com/robovm/robovm/issues/132.
   * That makes this feature partly obsolete.
   *
   * @param extensions that are valid. Ignore case.
   * @return Collection of files that are extracted native libraries
   */
  def managedNatives(extensions:Set[String]) = Def.task[Seq[File]] {
    val jars = update.value.select(configurationFilter(ManagedNatives.name))
    val extractionDirectoryBase = target.value / "RoboVMExtractedNatives"
    val log = streams.value.log

    val nameFilter = new NameFilter {
      override def accept(name: String): Boolean = {
        val extension = {
          val i = name.lastIndexOf('.')
          if(i == -1){
            ""
          }else{
            name.substring(i+1)
          }
        }
        extensions.exists(_.equalsIgnoreCase(extension))
      }
    }

    val results = for(jar <- jars) yield {
      val targetDirectory = extractionDirectoryBase / (jar.getName + "-" + jar.lastModified())
      if(targetDirectory.isDirectory){
        log.debug("Not attempting to extract natives for "+ jar.getName + " - already extracted.")
      }else{
        if(targetDirectory.isFile){
          log.warn(jar.getName +" should be directory, is file, deleting.")
          if(!targetDirectory.delete())log.warn(jar.getName +" could not be deleted! Hoping for the best.")
        }
        log.info("Extracting natives from "+jar.getAbsolutePath+" to "+targetDirectory.getAbsolutePath)
        targetDirectory.mkdirs()
        FileUtils.cleanDirectory(targetDirectory)
        IO.unzip(jar, targetDirectory, nameFilter, preserveLastModified = false)
      }

      val results = new ArrayBuffer[File]()
      def includeNatives(directory:File): Unit ={
        for(file <- directory.listFiles() if !file.isHidden){
          if(file.isDirectory){
            includeNatives(file)
          }else if(file.isFile && nameFilter.accept(file)){
            results += file
          }
        }
      }
      includeNatives(targetDirectory)
      results:Iterable[File]
    }

    val result = results.flatten
    result.foreach(n => {log.debug("Using managed native: "+n.getName)})
    result
  }

  lazy val iOSManagedNatives = managedNatives(Set("a"))

  lazy val AndroidManagedNatives = managedNatives(Set("so"))

  val iOSRobovmXMLManagedNatives = Def.task[Elem] {
    <libs>
      {
        iOSManagedNatives.value.map{
          file =>
            <lib>{file.getAbsolutePath}</lib>
        }
      }
    </libs>
  }
}
