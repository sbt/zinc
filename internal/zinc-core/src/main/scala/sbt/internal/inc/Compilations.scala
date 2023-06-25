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

import xsbti.compile.analysis.ReadCompilations

/** Information about compiler runs accumulated since `clean` command has been run. */
trait Compilations extends ReadCompilations {
  def allCompilations: Seq[Compilation]
  def ++(o: Compilations): Compilations
  def add(c: Compilation): Compilations

  // Work around the fact that Array is invariant
  import xsbti.compile.analysis.{ Compilation => CompilationInterface }
  override def getAllCompilations: Array[CompilationInterface] =
    allCompilations.toArray[CompilationInterface]
}

object Compilations {
  val empty: Compilations = new MCompilations(Seq.empty)
  def of(s: Seq[Compilation]): Compilations = new MCompilations(s)
  def merge(s: Traversable[Compilations]): Compilations =
    of((s flatMap { _.allCompilations }).toSeq.distinct)
}

private final class MCompilations(val allCompilations: Seq[Compilation]) extends Compilations {
  // TODO: Sort `allCompilations` chronologically and enforce it in the Zinc API specification

  def ++(o: Compilations): Compilations = new MCompilations(allCompilations ++ o.allCompilations)
  def add(c: Compilation): Compilations = new MCompilations(allCompilations :+ c)
}
