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

package sbt.internal.inc.classpath

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.net.URLClassLoader

object ClasspathFilterTest extends Properties:
  override def tests: List[Test] = List(
    example("loadClass surfaces JDK platform-module classes", platformModuleClasses),
  )

  private def platformModuleClasses: Result =
    val parent = new URLClassLoader(Array.empty, ClassLoader.getSystemClassLoader)
    val filter = new ClasspathFilter(parent, parent, Set.empty)
    val c = filter.loadClass("java.sql.Timestamp")
    c.getName ==== "java.sql.Timestamp"
