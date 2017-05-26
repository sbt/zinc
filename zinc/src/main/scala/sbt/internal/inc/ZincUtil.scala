/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File
import java.net.URLClassLoader

import sbt.internal.inc.classpath.ClassLoaderCache
import xsbti.compile._

/**
 * Define a private implementation of the static methods forwarded from [[ZincCompilerUtil]].
 */
object ZincUtil {

  /**
   * Return a fully-fledged, default incremental compiler ready to use.
   */
  def defaultIncrementalCompiler: IncrementalCompiler =
    new IncrementalCompilerImpl

  /**
   * Instantiate a Scala compiler that is instrumented to analyze dependencies.
   * This Scala compiler is useful to create your own instance of incremental
   * compilation.
   *
   * @see [[IncrementalCompiler]] for more details on creating your custom
   *     incremental compiler.
   *
   * @param scalaInstance The Scala instance to be used.
   * @param compilerBridgeJar The jar file or directory of the compiler bridge compiled for the given scala instance.
   * @param classpathOptions The options of all the classpath that the
   *                         compiler takes in.
   * @return A Scala compiler ready to be used.
   */
  def scalaCompiler(
      scalaInstance: xsbti.compile.ScalaInstance,
      compilerBridgeJar: File,
      classpathOptions: ClasspathOptions
  ): AnalyzingCompiler = {
    val bridgeProvider = CompilerBridgeProvider.constant(compilerBridgeJar, scalaInstance)
    val emptyHandler = (_: Seq[String]) => ()
    val loader = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(
      scalaInstance,
      bridgeProvider,
      classpathOptions,
      emptyHandler,
      loader
    )
  }

  /**
   * Instantiate a Scala compiler that is instrumented to analyze dependencies.
   * This Scala compiler is useful to create your own instance of incremental
   * compilation.
   *
   * @see [[IncrementalCompiler]] for more details on creating your custom
   *     incremental compiler.
   *
   * @param scalaInstance The Scala instance to be used.
   * @param compilerBridgeJar The jar file or directory of the compiler bridge compiled for the given scala instance.
   * @return A Scala compiler ready to be used.
   */
  def scalaCompiler(
      scalaInstance: xsbti.compile.ScalaInstance,
      compilerBridgeJar: File
  ): AnalyzingCompiler = {
    val optionsUtil = ClasspathOptionsUtil.boot
    scalaCompiler(scalaInstance, compilerBridgeJar, optionsUtil)
  }
}
