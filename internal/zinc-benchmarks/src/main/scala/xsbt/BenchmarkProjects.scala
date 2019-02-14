/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
