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

import java.nio.file.Path

import xsbti.{ FileConverter, VirtualFile }
import xsbti.compile.{
  ClasspathOptions,
  ClasspathOptionsUtil,
  JavaCompiler => XJavacompiler,
  JavaTools => XJavaTools,
  Javadoc => XJavadoc,
  ScalaInstance
}

/** Factory methods for getting a java toolchain. */
object JavaTools {

  /** Create a new aggregate tool from existing tools. */
  def apply(c: XJavacompiler, docgen: XJavadoc): XJavaTools = {
    new XJavaTools {
      override val javac = c
      override val javadoc = docgen
    }
  }

  /**
   * Construct a new set of java toolchain for incremental compilation.
   *
   * @param instance The Scala instance to be used for incremental compilation.
   *                 This is necessary to add Scala jars to the classpath.
   * @param options An instance of classpath options configured for this
   *                  incremental compilation that tell us, among other things,
   *                  whether we should append Scala to the classpath.
   * @param javaHome Option location of java home to run Javac.
   * @return An instance of Java tools that includes a [[JavaCompiler]].
   */
  def directOrFork(
      instance: ScalaInstance,
      options: ClasspathOptions,
      javaHome: Option[Path]
  ): XJavaTools = {
    val (javaCompiler, javaDoc) = javaHome match {
      case Some(_) =>
        (JavaCompiler.fork(javaHome), Javadoc.fork(javaHome))
      case _ =>
        val c = JavaCompiler.local.getOrElse(JavaCompiler.fork(None))
        val d = Javadoc.local.getOrElse(Javadoc.fork())
        (c, d)
    }
    apply(javaCompiler, javaDoc)
  }
}

/** Factory methods for constructing a java compiler. */
object JavaCompiler {

  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[XJavacompiler] = {
    Option(javax.tools.ToolProvider.getSystemJavaCompiler).map {
      (compiler: javax.tools.JavaCompiler) =>
        new LocalJavaCompiler(compiler)
    }
  }

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[Path] = None): XJavacompiler =
    new ForkedJavaCompiler(javaHome)

  /**
   * Create a collection of all the compiler arguments that must be
   * passed to a [[JavaCompiler]] from the current configuration.
   */
  def commandArguments(
      classpath: Seq[VirtualFile],
      converter: FileConverter,
      options: Seq[String],
      scalaInstance: ScalaInstance,
      cpOptions: ClasspathOptions
  ): Seq[String] = {
    val cp = classpath.map(converter.toPath)
    // this seems to duplicate scala-library
    val augmentedClasspath: Seq[Path] =
      if (!cpOptions.autoBoot) cp
      else cp ++ scalaInstance.libraryJars.map(_.toPath)
    val javaCp = ClasspathOptionsUtil.javac(cpOptions.compiler)
    val compilerArgs = new CompilerArguments(scalaInstance, javaCp)
    compilerArgs.makeArguments(Nil, augmentedClasspath, options)
  }
}

/** Factory methods for constructing a javadoc. */
object Javadoc {

  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[XJavadoc] = {
    if (LocalJava.hasLocalJavadoc) Some(new LocalJavadoc)
    else None
  }

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[Path] = None): XJavadoc =
    new ForkedJavadoc(javaHome)
}
