package sbtrobovm

import org.apache.commons.io.FileUtils
import sbt.Keys._
import sbt.{Def, _}

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
      targetDirectory.listFiles().filter(file => {
        !file.isDirectory && !file.isHidden && nameFilter.accept(file)
      })
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
