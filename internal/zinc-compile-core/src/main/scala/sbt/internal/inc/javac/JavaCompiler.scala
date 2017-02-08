/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt

package internal
package inc
package javac

import sbt.util.Logger
import java.io.{ PrintWriter, File }
import javax.tools.{ DiagnosticListener, Diagnostic, JavaFileObject, DiagnosticCollector }
import xsbti.compile.ScalaInstance
import xsbti.compile._
import xsbti.{ Severity, Reporter, Logger => XLogger }

/** Factory methods for getting a java toolchain. */
object JavaTools {
  /** Create a new aggregate tool from existing tools. */
  def apply(c: JavaCompiler, docgen: Javadoc): JavaTools =
    new JavaTools {
      override val javac = c
      override val javadoc = docgen
    }

  /**
   * Constructs a new set of java toolchain for incremental compilation.
   *
   * @param instance
   *            The scalaInstance being used in this incremental compile.  Used if we need to append
   *            scala to the classpath (yeah.... the classpath doesn't already have it).
   * @param cpOptions
   *            Classpath options configured for this incremental compiler. Basically, should we append scala or not.
   * @param javaHome
   *            If this is defined, the location where we should look for javac when we run.
   * @return
   *            A new set of the Java toolchain that also includes and instance of xsbti.compile.JavaCompiler
   */
  def directOrFork(instance: ScalaInstance, cpOptions: ClasspathOptions, javaHome: Option[File]): JavaTools = {
    val (compiler, doc) = javaHome match {
      case Some(_) => (JavaCompiler.fork(javaHome), Javadoc.fork(javaHome))
      case _ =>
        val c = JavaCompiler.local.getOrElse(JavaCompiler.fork(None))
        val d = Javadoc.local.getOrElse(Javadoc.fork())
        (c, d)
    }
    apply(compiler, doc)
  }
}

/** Factory methods for constructing a java compiler. */
object JavaCompiler {
  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[JavaCompiler] =
    for {
      compiler <- Option(javax.tools.ToolProvider.getSystemJavaCompiler)
    } yield new LocalJavaCompiler(compiler)

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[File] = None): JavaCompiler =
    new ForkedJavaCompiler(javaHome)

  def commandArguments(classpath: Seq[File], output: Output, options: Seq[String], scalaInstance: ScalaInstance, cpOptions: ClasspathOptions): Seq[String] =
    {
      // Oracle Javac doesn't support multiple output directories, but Eclipse provides their own compiler, which can call with MultipleOutput.
      // https://github.com/sbt/zinc/issues/163
      val target = output match {
        case so: SingleOutput   => Some(so.outputDirectory)
        case mo: MultipleOutput => None
      }
      val augmentedClasspath = if (cpOptions.autoBoot) classpath ++ Seq(scalaInstance.libraryJar) else classpath
      val javaCp = ClasspathOptionsUtil.javac(cpOptions.compiler)
      (new CompilerArguments(scalaInstance, javaCp))(Array[File](), augmentedClasspath, target, options)
    }
}

/** Factory methods for constructing a javadoc. */
object Javadoc {
  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[Javadoc] =
    // TODO - javax doc tool not supported in JDK6
    //Option(javax.tools.ToolProvider.getSystemDocumentationTool)
    if (LocalJava.hasLocalJavadoc) Some(new LocalJavadoc)
    else None

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[File] = None): Javadoc =
    new ForkedJavadoc(javaHome)

}

