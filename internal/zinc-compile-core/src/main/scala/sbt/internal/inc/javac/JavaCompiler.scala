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

import java.io.File

import xsbti.compile.{
  ClasspathOptions,
  JavaCompiler,
  JavaTools,
  Javadoc,
  MultipleOutput,
  Output,
  ScalaInstance,
  SingleOutput
}

/** Factory methods for getting a java toolchain. */
object JavaTools {

  /** Create a new aggregate tool from existing tools. */
  def apply(c: JavaCompiler, docgen: Javadoc): JavaTools = {
    new JavaTools {
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
    javaHome: Option[File]
  ): JavaTools = {
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
  def local: Option[JavaCompiler] = {
    Option(javax.tools.ToolProvider.getSystemJavaCompiler).map {
      (compiler: javax.tools.JavaCompiler) =>
        new LocalJavaCompiler(compiler)
    }
  }

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[File] = None): JavaCompiler =
    new ForkedJavaCompiler(javaHome)

  /**
   * Create a collection of all the compiler arguments that must be
   * passed to a [[JavaCompiler]] from the current configuration.
   */
  def commandArguments(
    classpath: Seq[File],
    output: Output,
    options: Seq[String],
    scalaInstance: ScalaInstance,
    cpOptions: ClasspathOptions
  ): Seq[String] = {
    /* Oracle Javac doesn't support multiple output directories
     * However, we use multiple output directories in case the
     * user provides their own Javac compiler that can indeed
     * make use of it (e.g. the Eclipse compiler does this via EJC).
     * See https://github.com/sbt/zinc/issues/163. */
    val target = output match {
      case so: SingleOutput  => Some(so.outputDirectory)
      case _: MultipleOutput => None
    }
    // TODO(jvican): Fix the `libraryJar` deprecation.
    val augmentedClasspath =
      if (!cpOptions.autoBoot) classpath
      else classpath ++ Seq(scalaInstance.libraryJar)
    val javaCp = ClasspathOptionsUtil.javac(cpOptions.compiler)
    val compilerArgs = new CompilerArguments(scalaInstance, javaCp)
    compilerArgs(Array[File](), augmentedClasspath, target, options)
  }
}

/** Factory methods for constructing a javadoc. */
object Javadoc {

  /** Returns a local compiler, if the current runtime supports it. */
  def local: Option[Javadoc] = {
    // TODO - javax doc tool not supported in JDK6
    //Option(javax.tools.ToolProvider.getSystemDocumentationTool)
    if (LocalJava.hasLocalJavadoc) Some(new LocalJavadoc)
    else None
  }

  /** Returns a local compiler that will fork javac when needed. */
  def fork(javaHome: Option[File] = None): Javadoc =
    new ForkedJavadoc(javaHome)
}
