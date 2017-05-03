/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc.cached

import sbt.internal.inc.cached.{ CacheProvider, CompilationCache, ProjectRebasedCache }

import xsbti.compile.{ CompileAnalysis, MiniSetup }

class ProjectRebaseCacheSpec extends CommonCachedCompilation("Project based cache") {

  override def remoteCacheProvider() = new CacheProvider {
    override def findCache(
        previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache] =
      Some(
        ProjectRebasedCache(remoteProject.baseLocation, remoteProject.defaultStoreLocation.toPath))
  }

}
