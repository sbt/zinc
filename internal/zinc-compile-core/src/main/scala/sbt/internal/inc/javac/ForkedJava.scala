/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt

package internal
package inc
package javac

import java.io.File
import java.nio.file.Path
import sbt.io.IO
import sbt.util.Logger
import xsbti.{ PathBasedFile, Reporter, Logger => XLogger, VirtualFile }
import xsbti.compile.{ IncToolOptions, JavaCompiler => XJavaCompiler, Javadoc => XJavadoc, Output }

import scala.sys.process.Process

/** Helper methods for running the java toolchain by forking. */
object ForkedJava {

  /** Helper method to launch programs. */
  private[javac] def launch(
      javaHome: Option[Path],
      program: String,
      sources0: Seq[VirtualFile],
      options: Seq[String],
      output: Output,
      log: Logger,
      reporter: Reporter
  ): Boolean = {
    val (jArgs, nonJArgs) = options.partition(_.startsWith("-J"))
    val outputOption = CompilerArguments.outputOption(output)
    // val sources: Seq[String] = sources0.map(converter.toPath).map(_.toAbsolutePath.toString)
    val sources = sources0 map {
      case x: PathBasedFile => x.toPath.toAbsolutePath.toString
    }
    val allArguments = outputOption ++ nonJArgs ++ sources

    withArgumentFile(allArguments) { argsFile =>
      val forkArgs = jArgs :+ s"@${normalizeSlash(argsFile.getAbsolutePath)}"
      val exe = getJavaExecutable(javaHome, program)
      val cwd = new File(new File(".").getAbsolutePath).getCanonicalFile
      val javacLogger = new JavacLogger(log, reporter, cwd)
      var exitCode = -1
      try {
        exitCode = Process(exe +: forkArgs, cwd) ! javacLogger
      } finally {
        javacLogger.flush(program, exitCode)
      }
      // We return true or false, depending on success.
      exitCode == 0
    }
  }

  /**
   * Helper method to create an argument file that we pass to Javac.  Gets over the windows
   * command line length limitation.
   * @param args The string arguments to pass to Javac.
   * @param f  A function which is passed the arg file.
   * @tparam T The return type.
   * @return  The result of using the argument file.
   */
  def withArgumentFile[T](args: Seq[String])(f: File => T): T = {
    import IO.{ Newline, withTemporaryDirectory, write }
    withTemporaryDirectory { tmp =>
      val argFile = new File(tmp, "argfile")
      write(argFile, args.map(escapeSpaces).mkString(Newline))
      f(argFile)
    }
  }
  // javac's argument file seems to allow naive space escaping with quotes.  escaping a quote with a backslash does not work
  private def escapeSpaces(s: String): String = '\"' + normalizeSlash(s) + '\"'
  private def normalizeSlash(s: String) = s.replace(File.separatorChar, '/')

  /** create the executable name for java */
  private[javac] def getJavaExecutable(javaHome: Option[Path], name: String): String =
    javaHome match {
      case None => name
      case Some(jh) =>
        jh.resolve("bin").resolve(name).toAbsolutePath.toString
    }
}

/** An implementation of compiling java which forks a Javac instance. */
final class ForkedJavaCompiler(javaHome: Option[Path]) extends XJavaCompiler {
  def run(
      sources: Array[VirtualFile],
      options: Array[String],
      output: Output,
      incToolOptions: IncToolOptions,
      reporter: Reporter,
      log: XLogger
  ): Boolean =
    ForkedJava.launch(
      javaHome,
      "javac",
      sources.toIndexedSeq,
      options.toIndexedSeq,
      output,
      log,
      reporter,
    )
}
final class ForkedJavadoc(javaHome: Option[Path]) extends XJavadoc {
  def run(
      sources: Array[VirtualFile],
      options: Array[String],
      output: Output,
      incToolOptions: IncToolOptions,
      reporter: Reporter,
      log: XLogger
  ): Boolean =
    ForkedJava.launch(
      javaHome,
      "javadoc",
      sources.toIndexedSeq,
      options.toIndexedSeq,
      output,
      log,
      reporter,
    )
}
