package sbt.internal.inc

import java.io.File

import xsbti.compile.analysis.RootPaths

case class ConcreteRootPaths(sourceRoot: File, libraryRoot: File, productRoot: File)
    extends RootPaths {
  override def getSourcesRootPath: File = sourceRoot
  override def getLibrariesRootPath: File = libraryRoot
  override def getProductsRootPath: File = productRoot
}
