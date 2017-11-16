/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
