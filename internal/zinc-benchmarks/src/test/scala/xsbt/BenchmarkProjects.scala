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

package xsbt

object BenchmarkProjects {
  object Shapeless
      extends BenchmarkProject(
        "milessabin/shapeless",
        "19a9c3a5951e4197084285bd568c2fef6717ef81",
        List("coreJVM")
      )

  object Scalac
      extends BenchmarkProject(
        "scala/scala",
        "df355f9d2dcc945f8c772a8b9f74300aef0f959a",
        List("library")
      )
}
