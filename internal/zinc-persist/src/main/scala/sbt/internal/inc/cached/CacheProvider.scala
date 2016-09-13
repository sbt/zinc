package sbt.internal.inc.cached

import xsbti.compile.{ CompileAnalysis, MiniSetup }

trait CacheProvider {
  def findCache(previous: Option[(CompileAnalysis, MiniSetup)]): Option[CompilationCache]
}
