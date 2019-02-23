/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import xsbti.compile.Output

import scala.runtime.ScalaRunTime

/**
 * Note that this implementation of the interface is part of the public Zinc Scala API.
 */
final class Compilation(startTime: Long, output: Output)
    extends xsbti.compile.analysis.Compilation {

  override def getOutput: Output = output
  override def getStartTime: Long = startTime
  private val product = (startTime, output)
  override def hashCode(): Int = ScalaRunTime._hashCode(product)
  override def equals(o: scala.Any): Boolean = o match {
    case c2: Compilation => startTime == c2.getStartTime && output == c2.getOutput
    case _               => false
  }
}

object Compilation {

  /** Instantiate a [[Compilation]] from a given output. */
  def apply(output: Output): Compilation = new Compilation(System.currentTimeMillis(), output)
}
