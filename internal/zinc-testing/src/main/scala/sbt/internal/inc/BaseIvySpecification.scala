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
import Configurations._

import scalajson.ast.unsafe.JValue

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

    val moduleSetting: ModuleSettings = InlineConfiguration(
      validate = false,
      scalaModuleInfo = scalaModuleInfo,
      module = moduleId,
      moduleInfo = ModuleInfo("foo"),
      dependencies = deps
    ).withConfigurations(configurations)
    val ivySbt = new IvySbt(mkIvyConfiguration(uo))
    new ivySbt.Module(moduleSetting)
  }

  def resolvers: Vector[Resolver] = Vector(DefaultMavenRepository)

  def chainResolver = ChainedResolver("sbt-chain", resolvers)

  def mkIvyConfiguration(uo: UpdateOptions): IvyConfiguration =
    new InlineIvyConfiguration(
      paths = IvyPaths(currentBase, Some(currentTarget)),
      resolvers = resolvers,
      otherResolvers = Vector.empty,
      moduleConfigurations = Vector(ModuleConfiguration("*", chainResolver)),
      lock = None,
      checksums = Vector.empty,
      managedChecksums = false,
      resolutionCacheDir = Some(currentTarget / "resolution-cache"),
      updateOptions = uo,
      log = log
    )

  def makeUpdateConfiguration: UpdateConfiguration = {
    val retrieveConfig =
      RetrieveConfiguration(currentManaged, Resolver.defaultRetrievePattern, false, Vector.empty)
    UpdateConfiguration(
      retrieveManaged = Some(retrieveConfig),
      missingOk = Some(false),
      logging = Some(UpdateLogging.Full),
      logicalClock = Some(LogicalClock.unknown),
      metadataDirectory = None,
      artifactFilter = Some(ArtifactTypeFilter.forbid(Set("src", "doc"))),
      offline = Some(false),
      frozen = Some(false)
    )
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
    PublishConfiguration(
      metadataFile = None,
      resolverName = Some(resolver.name),
      artifacts = artifacts.toVector,
      checksums = Some(Vector.empty),
      logging = Some(UpdateLogging.Full),
      overwrite = Some(true)
    )
  }

  def ivyPublish(module: IvySbt#Module, config: PublishConfiguration) = {
    IvyActions.publish(module, config, log)
  }
}
