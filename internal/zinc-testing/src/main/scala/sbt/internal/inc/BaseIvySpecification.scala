/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

// copy pasted from sbt/librarydependency
package sbt
package internal
package inc

import sbt.io.IO
import sbt.io.syntax._
import java.io.File
import sbt.internal.librarymanagement._
import sbt.internal.librarymanagement.cross.CrossVersionUtil
import sbt.librarymanagement._
import sbt.librarymanagement.ivy._
import Configurations._

trait BaseIvySpecification extends UnitSpec {
  def currentBase: File = new File(".")
  def currentTarget: File = currentBase / "target" / "ivyhome"
  def currentManaged: File = currentBase / "target" / "lib_managed"
  def currentDependency: File = currentBase / "target" / "dependency"
  def defaultModuleId: ModuleID =
    ModuleID("com.example", "foo", "0.1.0").withConfigurations(Some("compile"))

  def configurations = Vector(Compile, Test, Runtime)
  def module(moduleId: ModuleID,
             deps: Vector[ModuleID],
             scalaFullVersion: Option[String],
             uo: UpdateOptions = UpdateOptions(),
             overrideScalaVersion: Boolean = true): IvySbt#Module = {
    val scalaModuleInfo = scalaFullVersion map { fv =>
      ScalaModuleInfo(
        scalaFullVersion = fv,
        scalaBinaryVersion = CrossVersionUtil.binaryScalaVersion(fv),
        configurations = Vector.empty,
        checkExplicit = true,
        filterImplicit = false,
        overrideScalaVersion = overrideScalaVersion
      )
    }

    val moduleSetting: ModuleSettings = ModuleDescriptorConfiguration(moduleId, ModuleInfo("foo"))
      .withDependencies(deps)
      .withConfigurations(configurations)
      .withScalaModuleInfo(scalaModuleInfo)
    val ivySbt = new IvySbt(mkIvyConfiguration(uo))
    new ivySbt.Module(moduleSetting)
  }

  def resolvers: Vector[Resolver] = Vector(Resolver.mavenCentral)

  def chainResolver = ChainedResolver("sbt-chain", resolvers)

  def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration = {
    val moduleConfs = Vector(ModuleConfiguration("*", chainResolver))
    val resCacheDir = currentTarget / "resolution-cache"
    InlineIvyConfiguration()
      .withPaths(IvyPaths(currentTarget, Some(currentTarget)))
      .withResolvers(resolvers)
      .withModuleConfigurations(moduleConfs)
      .withChecksums(Vector.empty)
      .withResolutionCacheDir(resCacheDir)
      .withLog(log)
      .withUpdateOptions(uo)
  }

  def makeUpdateConfiguration: UpdateConfiguration = {
    val retrieveConfig =
      RetrieveConfiguration(currentManaged, Resolver.defaultRetrievePattern, false, Vector.empty)
    UpdateConfiguration()
      .withRetrieveManaged(retrieveConfig)
      .withLogging(UpdateLogging.Full)
  }

  def ivyUpdateEither(module: IvySbt#Module): Either[UnresolvedWarning, UpdateReport] = {
    // IO.delete(currentTarget)
    val config = makeUpdateConfiguration
    IvyActions.updateEither(module, config, UnresolvedWarningConfiguration(), log)
  }

  def cleanIvyCache(): Unit = IO.delete(currentTarget / "cache")

  def cleanCachedResolutionCache(module: IvySbt#Module): Unit =
    IvyActions.cleanCachedResolutionCache(module, log)

  def ivyUpdate(module: IvySbt#Module) =
    ivyUpdateEither(module) match {
      case Right(r) => r
      case Left(w) =>
        throw w.resolveException
    }

  def mkPublishConfiguration(resolver: Resolver,
                             artifacts: Map[Artifact, File]): PublishConfiguration = {
    PublishConfiguration()
      .withResolverName(resolver.name)
      .withArtifacts(artifacts.toVector)
      .withChecksums(Vector.empty)
      .withLogging(UpdateLogging.Full)
      .withOverwrite(true)
  }

  def ivyPublish(module: IvySbt#Module, config: PublishConfiguration) = {
    IvyActions.publish(module, config, log)
  }
}
