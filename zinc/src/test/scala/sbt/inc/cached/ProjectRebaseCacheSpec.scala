/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc.cached

import java.io.File
import java.nio.file.{ Files, Path, Paths }

import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }
import sbt.internal.inc.cached.{ CacheAwareStore, CacheProvider, CompilationCache, ProjectRebasedCache }
import sbt.internal.inc.{ Analysis, AnalysisStore, BridgeProviderSpecification, FileBasedStore }
import sbt.io.IO
import sbt.inc.BaseCompilerSpec
import xsbti.compile.{ CompileAnalysis, MiniSetup }

class ProjectRebaseCacheSpec extends CommonCachedCompilation("Project based cache") {

  override def remoteCacheProvider() = new CacheProvider {
    override def findCache(previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache] =
      Some(ProjectRebasedCache(remoteProject.baseLocation, remoteProject.defaultStoreLocation.toPath))
  }

}
