package sbt.internal.inc

import java.io.File

import xsbti.compile.OutputGroup

case class SimpleOutputGroup(sourceDirectory: File, outputDirectory: File) extends OutputGroup
