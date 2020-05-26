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

package sbt.internal.inc.text

import sbinary.DefaultProtocol._
import sbinary._
import sbt.internal.inc.APIs.emptyCompanions
import sbt.internal.inc.Compilation
import xsbti.api.{ AnalyzedClass, NameHash, SafeLazyProxy }

object AnalyzedClassFormats {
  // This will throw out API information intentionally.
  def analyzedClassFormat(
      implicit ev0: Format[Compilation],
      ev1: Format[NameHash]
  ): Format[AnalyzedClass] =
    wrap[AnalyzedClass, (Long, String, Int, Array[NameHash], Boolean, String)](
      a => (a.compilationTimestamp(), a.name, a.apiHash, a.nameHashes, a.hasMacro, a.provenance),
      (x: (Long, String, Int, Array[NameHash], Boolean, String)) =>
        x match {
          case (
              compilationTimestamp: Long,
              name: String,
              apiHash: Int,
              nameHashes: Array[NameHash],
              hasMacro: Boolean,
              provenance: String
              ) =>
            val _ = ev0.toString
            AnalyzedClass.of(
              compilationTimestamp,
              name,
              SafeLazyProxy(emptyCompanions),
              apiHash,
              nameHashes,
              hasMacro,
              apiHash,
              provenance
            )
        }
    )
}
