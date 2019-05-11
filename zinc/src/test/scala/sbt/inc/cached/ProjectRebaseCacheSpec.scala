/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
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
