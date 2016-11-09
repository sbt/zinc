/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt
package internal
package inc
package classfile

import scala.collection.mutable
import mutable.{ ArrayBuffer, Buffer }
import scala.annotation.tailrec
import java.io.File
import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Modifier.{ STATIC, PUBLIC, ABSTRACT }
import java.net.URL
import xsbti.api.DependencyContext
import xsbti.api.DependencyContext._
import sbt.io.IO
import sbt.util.Logger

private[sbt] object Analyze {
  def apply[T](newClasses: Seq[File], sources: Seq[File], log: Logger)(analysis: xsbti.AnalysisCallback, loader: ClassLoader, readAPI: (File, Seq[Class[_]]) => Set[(String, String)]): Unit = {
    val sourceMap = sources.toSet[File].groupBy(_.getName)

    def load(tpe: String, errMsg: => Option[String]): Option[Class[_]] =
      try { Some(Class.forName(tpe, false, loader)) }
      catch { case e: Throwable => errMsg.foreach(msg => log.warn(msg + " : " + e.toString)); None }

    val productToClassName = mutable.Set.empty[String]
    val sourceToClassFiles = mutable.HashMap[File, Buffer[ClassFile]](
      sources zip Seq.fill(sources.size)(new ArrayBuffer[ClassFile]): _*
    )

    val binaryClassNameToLoadedClass = new mutable.HashMap[String, Class[_]]

    val classfilesCache = mutable.Map.empty[String, File]

    // parse class files and assign classes to sources.  This must be done before dependencies, since the information comes
    // as class->class dependencies that must be mapped back to source->class dependencies using the source+class assignment
    for (
      newClass <- newClasses;
      classFile = Parser(newClass);
      sourceFile <- classFile.sourceFile orElse guessSourceName(newClass.getName);
      source <- guessSourcePath(sourceMap, classFile, log);
      binaryClassName = classFile.className;
      loadedClass <- load(binaryClassName, Some("Error reading API from class file"))
    ) {
      binaryClassNameToLoadedClass.update(binaryClassName, loadedClass)
      val srcClassName = binaryToSourceName(loadedClass)
        .orElse(Option(loadedClass.getEnclosingClass).flatMap(binaryToSourceName))

      srcClassName match {
        case Some(srcClassName) =>
          analysis.generatedNonLocalClass(source, newClass, binaryClassName, srcClassName)
          productToClassName += srcClassName
        case None => analysis.generatedLocalClass(source, newClass)
      }

      sourceToClassFiles(source) += classFile
    }

    // get class to class dependencies and map back to source to class dependencies
    for ((source, classFiles) <- sourceToClassFiles) {
      analysis.startSource(source)
      val loadedNonLocalClasses =
        classFiles.map(c => binaryClassNameToLoadedClass(c.className)).filter(_.getCanonicalName != null)
      val publicInherited: Map[String, Set[String]] =
        readAPI(source, loadedNonLocalClasses).groupBy(_._1).mapValues(_.map(_._2))

      def processDependency(onBinaryName: String, context: DependencyContext, fromBinaryName: String): Unit = {
        val fromClassName = {
          val clsName = for {
            loadedClass <- binaryClassNameToLoadedClass.get(fromBinaryName)
            sourceName <- binaryToSourceName(loadedClass)
          } yield sourceName
          clsName match {
            case Some(resolvedClassName) => resolvedClassName
            // TODO: handle dependencies coming from local classes the same way as it's done for Scala
            // classes, see: https://github.com/sbt/sbt/issues/1104#issuecomment-169146039
            case None                    => return
          }
        }

        def loadFromClassloader(): Option[File] = for {
          url <- Option(loader.getResource(onBinaryName.replace('.', '/') + ClassExt))
          file <- urlAsFile(url, log)
        } yield {
          classfilesCache(onBinaryName) = file
          file
        }

        trapAndLog(log) {
          val scalaLikeTypeName = onBinaryName.replace('$', '.')

          if (productToClassName.contains(scalaLikeTypeName))
            analysis.classDependency(scalaLikeTypeName, fromClassName, context)
          else
            for (file <- classfilesCache.get(onBinaryName).orElse(loadFromClassloader()))
              analysis.binaryDependency(file, onBinaryName, fromClassName, source, context)
        }
      }
      def processDependencies(binaryClassNames: Iterable[String], context: DependencyContext, fromBinaryClassName: String): Unit =
        binaryClassNames.foreach(binaryClassName => processDependency(binaryClassName, context, fromBinaryClassName))

      val typesInSource = classFiles.map(cf => cf.className -> cf.types).toMap
      typesInSource foreach {
        case (binaryClassName, binaryClassNameDeps) =>
          processDependencies(binaryClassNameDeps, DependencyByMemberRef, binaryClassName)
      }
      publicInherited foreach {
        case (className, inheritanceDeps) => processDependencies(inheritanceDeps, DependencyByInheritance, className)
      }
    }
  }
  private[this] def urlAsFile(url: URL, log: Logger): Option[File] =
    try IO.urlAsFile(url)
    catch {
      case e: Exception =>
        log.warn("Could not convert URL '" + url.toExternalForm + "' to File: " + e.toString)
        None
    }
  private def trapAndLog(log: Logger)(execute: => Unit): Unit = {
    try { execute }
    catch { case e: Throwable => log.trace(e); log.error(e.toString) }
  }
  private def guessSourceName(name: String) = Some(takeToDollar(trimClassExt(name)))
  private def takeToDollar(name: String) =
    {
      val dollar = name.indexOf('$')
      if (dollar < 0) name else name.substring(0, dollar)
    }
  private final val ClassExt = ".class"
  private def trimClassExt(name: String) = if (name.endsWith(ClassExt)) name.substring(0, name.length - ClassExt.length) else name
  private def binaryToSourceName(loadedClass: Class[_]): Option[String] = Option(loadedClass.getCanonicalName)
  private def guessSourcePath(sourceNameMap: Map[String, Set[File]], classFile: ClassFile, log: Logger) =
    {
      val classNameParts = classFile.className.split("""\.""")
      val pkg = classNameParts.init
      val simpleClassName = classNameParts.last
      val sourceFileName = classFile.sourceFile.getOrElse(simpleClassName.takeWhile(_ != '$').mkString("", "", ".java"))
      val candidates = findSource(sourceNameMap, pkg.toList, sourceFileName)
      candidates match {
        case Nil         => log.warn("Could not determine source for class " + classFile.className)
        case head :: Nil => ()
        case _           => log.warn("Multiple sources matched for class " + classFile.className + ": " + candidates.mkString(", "))
      }
      candidates
    }
  private def findSource(sourceNameMap: Map[String, Iterable[File]], pkg: List[String], sourceFileName: String): List[File] =
    refine((sourceNameMap get sourceFileName).toList.flatten.map { x => (x, x.getParentFile) }, pkg.reverse)

  @tailrec private def refine(sources: List[(File, File)], pkgRev: List[String]): List[File] =
    {
      def make = sources.map(_._1)
      if (sources.isEmpty || sources.tail.isEmpty)
        make
      else
        pkgRev match {
          case Nil => shortest(make)
          case x :: xs =>
            val retain = sources flatMap {
              case (src, pre) =>
                if (pre != null && pre.getName == x)
                  (src, pre.getParentFile) :: Nil
                else
                  Nil
            }
            refine(retain, xs)
        }
    }
  private def shortest(files: List[File]): List[File] =
    if (files.isEmpty) files
    else {
      val fs = files.groupBy(distanceToRoot(0))
      fs(fs.keys.min)
    }

  private def distanceToRoot(acc: Int)(file: File): Int =
    if (file == null) acc else distanceToRoot(acc + 1)(file.getParentFile)

  private def isTopLevel(classFile: ClassFile) = classFile.className.indexOf('$') < 0
  private lazy val unit = classOf[Unit]
  private lazy val strArray = List(classOf[Array[String]])

  private def isMain(method: Method): Boolean =
    method.getName == "main" &&
      isMain(method.getModifiers) &&
      method.getReturnType == unit &&
      method.getParameterTypes.toList == strArray
  private def isMain(modifiers: Int): Boolean = (modifiers & mainModifiers) == mainModifiers && (modifiers & notMainModifiers) == 0

  private val mainModifiers = STATIC | PUBLIC
  private val notMainModifiers = ABSTRACT
}
