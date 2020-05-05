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

package sbt
package internal
package inc

import java.nio.file.Path
import java.util.{ Map => jMap }
import java.util.Optional

import sbt.util.InterfaceUtil
import xsbti.api.AnalyzedClass
import xsbti.compile.ExternalHooks

object Hooks {
  private val QUICK_API = "QUICKAPI"
  private val GET_PROVENANCE = "GETPROVENANCE"

  /**
   * None => Found class somewhere outside of project.  No analysis possible.
   * Some(analyzed) if analyzed.provenance.isEmpty => Couldn't find it.
   * Some(analyzed) => good
   */
  private[sbt] def quickAPI(hooks: ExternalHooks): String => Option[AnalyzedClass] = {
    val f = getOrElse(hooks, QUICK_API, (_: String) => Optional.empty[AnalyzedClass])
    c => InterfaceUtil.toOption(f(c))
  }

  def addQuickAPI(m: jMap[String, Object], f: String => Optional[AnalyzedClass]): Unit = {
    m.put(QUICK_API, f)
    ()
  }

  private[sbt] def getProvenance(hooks: ExternalHooks): Path => String = {
    getOrElse(hooks, GET_PROVENANCE, (_: Path) => "")
  }

  def addGetProvenance(m: jMap[String, Object], f: Path => String): Unit = {
    m.put(GET_PROVENANCE, f)
    ()
  }

  private def getOrElse[A <: AnyRef](hooks: ExternalHooks, key: String, alt: A): A =
    hooks.extraHooks().getOrDefault(key, alt).asInstanceOf[A]
}
