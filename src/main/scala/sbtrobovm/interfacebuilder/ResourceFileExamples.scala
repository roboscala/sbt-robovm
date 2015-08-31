package sbtrobovm.interfacebuilder

import java.io.File

import sbt.IO
import sbt.complete.ExampleSource

import scala.collection.mutable.ArrayBuffer

/**
 * Custom version of sbt.complete.FileExamples, which shows only files inside given resource folders
 * @author Darkyen
 */
class ResourceFileExamples(base: File, resources:Set[File], sampleFilename:(String, String), prefix: String = "") extends ExampleSource {

  override def withAddedPrefix(addedPrefix: String): ResourceFileExamples = new ResourceFileExamples(base, resources, sampleFilename, prefix + addedPrefix)

  /**
   * Converts the file to Seq of parent directories,
   * with first entry being the first directory in path
   */
  private def fileToPathList(file:File):List[File] = {
    var head = file.getCanonicalFile
    var result:List[File] = Nil
    while(head != null){
      result = head :: result
      head = head.getParentFile
    }
    result
  }

  private def prefixLength[T](first:List[T], second:List[T]):Int = {
    var f = first
    var s = second
    var result = 0
    while(f.nonEmpty && s.nonEmpty){
      if(f.head == s.head){
        result += 1
        f = f.tail
        s = s.tail
      }else return result
    }
    result
  }

  private def relativePath(from:File, to:File):String = {
    val fromSeq = fileToPathList(from)
    val toSeq = fileToPathList(to)
    val prefix = prefixLength(fromSeq, toSeq)
    val toSeqPure = toSeq.drop(prefix)

    val result = new StringBuilder
    for(_ <- prefix until fromSeq.length)result.append("../")
    for(to <- toSeqPure)result.append(to.getName).append('/')
    result.toString()
  }

  private def inResourceFolder(currentFile:File): File ={
    val currently = fileToPathList(currentFile)
    for(resDir <- resources){
      val res = fileToPathList(resDir)
      val common = prefixLength(currently, res)
      if(common == res.length)return resDir
    }
    null
  }

  def currentDirectory():(File, String, String) = {
    val currentlyAt = new File(base.getCanonicalPath + "/" + prefix).getCanonicalFile
    if(currentlyAt.exists()){
      //Complete path
      (currentlyAt, "", if(currentlyAt.isDirectory && !prefix.endsWith("/")) "/" else "")
    }else{
      //Incomplete path
      (currentlyAt.getParentFile, currentlyAt.getName, "")
    }
  }

  /**
   * Suggestion strategy:
   * - When not in resource folder, suggest them
   * - When in resource folder, suggest folders around
   * - When no folders around, suggest creating a file
   */
  override def apply(): Stream[String] = {
    val result = new ArrayBuffer[String]()
    val (base, prefix, suggestedPrefix) = currentDirectory()

    val currentResourceFolder = inResourceFolder(base)
    if(currentResourceFolder == null){
      //Print paths to all resource folders
      for(resDir <- resources){
        result += relativePath(base, resDir)
      }
    }else{
      result ++= IO.listFiles(base)
        .filter(f => f.isDirectory && f.getName.startsWith(prefix))
        .map(f => suggestedPrefix + f.getName.substring(prefix.length) + (if (f.isDirectory) "/" else ""))

      if(result.isEmpty){
        //When no files found, suggest creating one
        if(prefix.isEmpty){
          result += sampleFilename._1 + '.' + sampleFilename._2
        }else if(prefix.endsWith(".")){
          //Just add the right extension
          result += sampleFilename._2
        }else if(!prefix.contains(".")){
          //Does not have extension yet, add it
          result += "." + sampleFilename._2
        }else{
          //Can't do anything more, suppress error
          result += ""
        }
      }
    }
    result.toStream
  }
}
