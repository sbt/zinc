/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc
package classfile

import java.io.File
import java.net.URLClassLoader
import javax.tools.{ StandardLocation, ToolProvider }

import sbt.io.IO
import sbt.internal.util.ConsoleLogger
import xsbti.api.DependencyContext._
import xsbti.{ AnalysisCallback, TestCallback }
import xsbti.TestCallback.ExtractedClassDependencies

import scala.collection.JavaConverters._

object JavaCompilerForUnitTesting {

  def extractDependenciesFromSrcs(srcs: (String, String)*): ExtractedClassDependencies = {
    val (_, testCallback) = compileJavaSrcs(srcs: _*)((_, _, classes) => extractParents(classes))

    val memberRefDeps = testCallback.classDependencies collect {
      case (target, src, DependencyByMemberRef) => (src, target)
    }
    val inheritanceDeps = testCallback.classDependencies collect {
      case (target, src, DependencyByInheritance) => (src, target)
    }
    val localInheritanceDeps = testCallback.classDependencies collect {
      case (target, src, LocalDependencyByInheritance) => (src, target)
    }
    ExtractedClassDependencies.fromPairs(memberRefDeps, inheritanceDeps, localInheritanceDeps)
  }

  def compileJavaSrcs(srcs: (String, String)*)(
      readAPI: (AnalysisCallback, File, Seq[Class[_]]) => Set[(String, String)])
    : (Seq[File], TestCallback) = {
    IO.withTemporaryDirectory { temp =>
      val srcFiles = srcs.map {
        case (fileName, src) => prepareSrcFile(temp, fileName, src)
      }
      val analysisCallback = new TestCallback
      val classesDir = new File(temp, "classes")
      classesDir.mkdir()

      val compiler = ToolProvider.getSystemJavaCompiler()
      val fileManager = compiler.getStandardFileManager(null, null, null)
      fileManager.setLocation(StandardLocation.CLASS_OUTPUT, Seq(classesDir).asJava)
      val compilationUnits = fileManager.getJavaFileObjectsFromFiles(srcFiles.asJava)
      compiler.getTask(null, fileManager, null, null, null, compilationUnits).call()
      fileManager.close()

      val classesFinder = sbt.io.PathFinder(classesDir) ** "*.class"
      val classFiles = classesFinder.get

      val classloader = new URLClassLoader(Array(classesDir.toURI.toURL))

      val logger = ConsoleLogger()

      // we pass extractParents as readAPI. In fact, Analyze expect readAPI to do both things:
      // - extract api representation out of Class (and saved it via a side effect)
      // - extract all base classes.
      // we extract just parents as this is enough for testing
      Analyze(classFiles, srcFiles, logger)(analysisCallback,
                                            classloader,
                                            readAPI(analysisCallback, _, _))
      (srcFiles, analysisCallback)
    }
  }

  private def prepareSrcFile(baseDir: File, fileName: String, src: String): File = {
    val srcFile = new File(baseDir, fileName)
    IO.write(srcFile, src)
    srcFile
  }

  private val extractParents: Seq[Class[_]] => Set[(String, String)] = { classes =>
    def canonicalNames(p: (Class[_], Class[_])): (String, String) =
      p._1.getCanonicalName -> p._2.getCanonicalName
    val parents = classes.map(c => c -> c.getSuperclass)
    val parentInterfaces = classes.flatMap(c => c.getInterfaces.map(i => c -> i))
    (parents ++ parentInterfaces).map(canonicalNames).toSet
  }
}
