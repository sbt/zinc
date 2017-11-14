/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.File

import sbt.io.IO
import sbt.io.syntax._
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import sbt.util.Logger
import xsbti.compile.CompilerBridgeProvider

/**
 * Base class for test suites that must be able to fetch and compile the compiler bridge.
 *
 * This is a very good example on how to instantiate the compiler bridge provider.
 */
abstract class BridgeProviderSpecification extends UnitSpec {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"

  private final val ZincScriptedLocal =
    s"$${user.dir}/.ivy2/zinc-scripted-local/${Resolver.localBasePattern}"
  private final val ScriptedResolver: Resolver = {
    import sbt.librarymanagement.{ FileRepository, Patterns }
    val toUse = Vector(ZincScriptedLocal)
    val ivyPattern = Patterns().withIsMavenCompatible(false)
    val finalPatterns = ivyPattern
      .withIvyPatterns(toUse)
      .withArtifactPatterns(toUse)
      .withSkipConsistencyCheck(true)
    FileRepository("zinc-scripted-local", Resolver.defaultFileConfiguration, finalPatterns)
  }

  // Use the scripted resolver to make sure that we don't mistakenly get user local jars
  val resolvers = Array(ScriptedResolver, Resolver.mavenCentral)
  private def ivyConfiguration =
    getDefaultConfiguration(currentBase, currentTarget, resolvers, log)

  // Place where we store the compiled and installed bridges for every Scala version
  def secondaryCacheDirectory: File = file("target").getAbsoluteFile./("zinc-components")

  def getZincProvider(targetDir: File, log: Logger): CompilerBridgeProvider = {
    val lock = ZincComponentCompiler.getDefaultLock
    val secondaryCache = Some(secondaryCacheDirectory)
    val componentProvider = ZincComponentCompiler.getDefaultComponentProvider(targetDir)
    val manager = new ZincComponentManager(lock, componentProvider, secondaryCache, log)
    val dependencyResolution = IvyDependencyResolution(ivyConfiguration)
    ZincComponentCompiler.interfaceProvider(manager, dependencyResolution, currentManaged)
  }

  def getCompilerBridge(targetDir: File, log: Logger, scalaVersion: String): File = {
    val provider = getZincProvider(targetDir, log)
    val scalaInstance = provider.fetchScalaInstance(scalaVersion, log)
    val bridge = provider.fetchCompiledBridge(scalaInstance, log)
    val target = targetDir / s"target-bridge-$scalaVersion.jar"
    IO.copyFile(bridge, target)
    target
  }

  def scalaInstance(scalaVersion: String,
                    targetDir: File,
                    logger: Logger): xsbti.compile.ScalaInstance = {
    val provider = getZincProvider(targetDir, logger)
    provider.fetchScalaInstance(scalaVersion, logger)
  }

  private def getDefaultConfiguration(baseDirectory: File,
                                      ivyHome: File,
                                      resolvers0: Array[Resolver],
                                      log: xsbti.Logger): InlineIvyConfiguration = {
    import sbt.io.syntax._
    val resolvers = resolvers0.toVector
    val chainResolver = ChainedResolver("zinc-chain", resolvers)
    InlineIvyConfiguration()
      .withPaths(IvyPaths(baseDirectory, Some(ivyHome)))
      .withResolvers(resolvers)
      .withModuleConfigurations(Vector(ModuleConfiguration("*", chainResolver)))
      .withLock(None)
      .withChecksums(Vector.empty)
      .withResolutionCacheDir(ivyHome / "resolution-cache")
      .withUpdateOptions(UpdateOptions())
      .withLog(log)
  }
}
