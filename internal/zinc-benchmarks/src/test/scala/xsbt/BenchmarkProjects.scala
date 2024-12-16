/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbt

object BenchmarkProjects {
  object Shapeless
      extends BenchmarkProject(
        "milessabin/shapeless",
        "62611554399e0d04466da95591253706b2d3020d",
        List("coreJVM")
      )

  object Scalac
      extends BenchmarkProject(
        "scala/scala",
        "31539736462078b1da615880ef11890a6538b45e",
        List("library")
      )
}
