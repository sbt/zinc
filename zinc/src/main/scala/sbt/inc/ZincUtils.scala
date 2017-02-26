/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

import java.io.File
import java.net.URLClassLoader

import xsbti.compile._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.internal.inc.{
  AnalyzingCompiler,
  ClasspathOptionsUtil,
  CompilerBridgeProvider,
  IncrementalCompilerImpl
}

/**
 * Define utils to get instance of the Zinc public API back living under
 * [[xsbti.compile]], extending [[xsbti.compile.IncrementalCompilerUtil]].
 *
 * @note These utils are modeled as a class to be Java-friendly.
 *       Scala consumers should use these helpers via the [[ZincUtils]] object.
 *
 */
class ZincUtils extends xsbti.compile.IncrementalCompilerUtil {
  /**
   * Return a fully-fledged, default incremental compiler ready to use.
   */
  override def defaultIncrementalCompiler: IncrementalCompiler =
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
   * @param compilerBridgeJar The jar file of the compiler bridge.
   * @param classpathOptions The options of all the classpath that the
   *                         compiler takes in.
   * @return A Scala compiler ready to be used.
   */
  override def scalaCompiler(
    scalaInstance: ScalaInstance,
    compilerBridgeJar: File,
    classpathOptions: ClasspathOptions
  ): AnalyzingCompiler = {
    val bridgeProvider = CompilerBridgeProvider.constant(compilerBridgeJar)
    val emptyHandler = (_: Seq[String]) => ()
    val loader = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    new AnalyzingCompiler(
      scalaInstance, bridgeProvider, classpathOptions, emptyHandler, loader
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
   * @param compilerBridgeJar The jar file of the compiler bridge.
   * @return A Scala compiler ready to be used.
   */
  override def scalaCompiler(
    scalaInstance: ScalaInstance,
    compilerBridgeJar: File
  ): AnalyzingCompiler = {
    val optionsUtil = ClasspathOptionsUtil.boot
    scalaCompiler(scalaInstance, compilerBridgeJar, optionsUtil)
  }
}

object ZincUtils extends ZincUtils
