/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import sbt.internal.util.FeedbackProvidedException
import xsbti.compile.{ ClasspathOptions, ScalaInstance => XScalaInstance }

/**
 * Provide a basic interface to the Scala Compiler that does not analyze
 * dependencies nor does any kind of incremental compilation.
 *
 * This interface is called in the same virtual machine where it's instantiated.
 * It's useful for raw compilation of sources, such as those of the compiler
 * interface (bridge) and plugin code.
 *
 * @param scalaInstance The Scala instance to which we create a compiler.
 * @param cp The classpath options that dictate which classpath entries to use.
 * @param log The logger where the Scalac compiler creation is reported.
 */
class RawCompiler(val scalaInstance: XScalaInstance, cp: ClasspathOptions, log: sbt.util.Logger) {

  /**
   * Run the compiler with the usual compiler inputs.
   *
   * This method supports both Scalac and Dotty. To use a concrete compiler,
   * you need to pass the [[ScalaInstance]] associated with it.
   *
   * @param sources The sources to be compiled.
   * @param classpath The classpath to be used at compile time.
   * @param outputDirectory The directory in which the class files are placed.
   * @param options The auxiliary options to Scala compilers.
   */
  def apply(sources: Seq[File], classpath: Seq[File], outputDirectory: File, options: Seq[String]): Unit = {

    /** Run the compiler and get the reporter attached to it. */
    def getReporter(fqn: String, args: Array[String], isDotty: Boolean): AnyRef = {
      val mainClass = Class.forName(fqn, true, scalaInstance.loader)
      val process = mainClass.getMethod("process", classOf[Array[String]])
      val potentialReporter = process.invoke(null, args)
      if (isDotty) potentialReporter
      else mainClass.getMethod("reporter").invoke(null)
    }

    // Make sure that methods exist so that reflection is safe (trick)
    import scala.tools.nsc.Main.{ process => _, reporter => _ }
    val uniqueCompilerVersion = scalaInstance.actualVersion
    val compilerOut = Some(outputDirectory)
    val arguments = compilerArguments(sources, classpath, compilerOut, options)
    val args = arguments.toArray

    log.debug(
      s"""Creating plain compiler interface for $uniqueCompilerVersion.
        |  > Arguments: ${arguments.mkString("\n\t", "\n\t", "")}
      """.stripMargin
    )

    val reporter = {
      if (ScalaInstance.isDotty(scalaInstance.version))
        getReporter("dotty.tools.dotc.Main", args, isDotty = true)
      else getReporter("scala.tools.nsc.Main", args, isDotty = false)
    }

    checkForFailure(reporter, arguments.toArray)
  }

  protected def checkForFailure(reporter: AnyRef, args: Array[String]): Unit = {
    val hasErrorsMethod = reporter.getClass.getMethod("hasErrors")
    val failed = hasErrorsMethod.invoke(reporter).asInstanceOf[Boolean]
    if (failed) throw new CompileFailed(args, "Plain compile failed", Array())
  }

  /**
   * Return the correct compiler arguments for the given [[ScalaInstance]]
   * and [[ClasspathOptions]]. Keep in mind that these compiler arguments are
   * specific to a concrete Scala version.
   */
  def compilerArguments = new CompilerArguments(scalaInstance, cp)
}

class CompileFailed(
  val arguments: Array[String],
  override val toString: String,
  val problems: Array[xsbti.Problem]
) extends xsbti.CompileFailed with FeedbackProvidedException
