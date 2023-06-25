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

package sbt.internal.inc

import java.io.File

import xsbti.compile.analysis.RootPaths

case class ConcreteRootPaths(sourceRoot: File, libraryRoot: File, productRoot: File)
    extends RootPaths {
  override def getSourcesRootPath: File = sourceRoot
  override def getLibrariesRootPath: File = libraryRoot
  override def getProductsRootPath: File = productRoot
}
