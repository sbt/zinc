package sbt.internal.inc

import java.io.File

import xsbti.compile.OutputGroup

case class SimpleOutputGroup(getSourceDirectory: File, getOutputDirectory: File)
    extends OutputGroup
