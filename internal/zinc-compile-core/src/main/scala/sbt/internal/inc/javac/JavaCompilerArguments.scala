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

package sbt
package internal
package inc
package javac

import xsbti.{ PathBasedFile, VirtualFile }
import CompilerArguments.{ absString, abs }

// Intended to be used with sbt.internal.inc.javac.JavaTools.
private[sbt] object JavaCompilerArguments {
  def apply(
      sources: List[VirtualFile],
      classpath: List[VirtualFile],
      options: List[String]
  ): List[String] = {
    val cp = classpath map {
      case x: PathBasedFile => x.toPath
    }
    val sources1 = sources map {
      case x: PathBasedFile => x.toPath
    }
    val classpathOption = List("-classpath", absString(cp))
    options ::: classpathOption ::: abs(sources1)
  }
}
