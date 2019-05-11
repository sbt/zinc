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
        "43140dbfa340f55bd753e2ab5977cc177debd559",
        List("coreJVM")
      )

  object Scalac
      extends BenchmarkProject(
        "scala/scala",
        "fa5ad9ac24b390a0863ed39796c3ab2e5760e2b4",
        List("library")
      )
}
