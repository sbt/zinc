/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File

import xsbti.T2
import xsbti.compile.{
  CompileOrder,
  MiniOptions,
  MiniSetup,
  MultipleOutput,
  OutputGroup,
  SingleOutput,
  Output => APIOutput
}

/**
 * Define all the implicit instances that are used in the Scala implementation
 * of the incremental compiler to check the mathematical equivalence relation
 * between two given classes.
 *
 * @see scala.math.Equiv for more information on this kind of equivalence.
 */
object MiniSetupUtil {

  /* *********************************************************************** */
  /*  Don't replace `new`s by SAM conversions, those are only in Scala 2.12. */
  /* *********************************************************************** */

  /* Define first because `Equiv[CompileOrder.value]` dominates `Equiv[MiniSetup]`. */
  implicit def equivCompileSetup(equivOpts: Equiv[MiniOptions])(
      implicit equivOutput: Equiv[APIOutput],
      equivComp: Equiv[String]
  ): Equiv[MiniSetup] = {
    new Equiv[MiniSetup] {
      def equiv(a: MiniSetup, b: MiniSetup) = {
        def sameOutput = equivOutput.equiv(a.output, b.output)
        def sameOptions = equivOpts.equiv(a.options, b.options)
        def sameCompiler = equivComp.equiv(a.compilerVersion, b.compilerVersion)
        def sameOrder = a.order == b.order
        def sameExtra = equivPairs.equiv(a.extra, b.extra)

        sameOutput &&
        sameOptions &&
        sameCompiler &&
        sameOrder &&
        sameExtra
      }
    }
  }

  implicit val equivPairs: Equiv[Array[T2[String, String]]] = {
    new Equiv[Array[T2[String, String]]] {
      private def comparable(
          a: Array[T2[String, String]]
      ): Set[(String, String)] = {
        a.filterNot(_.get1 startsWith "info.")
          .map(v => v.get1() -> v.get2())(collection.breakOut)
      }

      def equiv(
          a: Array[T2[String, String]],
          b: Array[T2[String, String]]
      ): Boolean = {
        comparable(a) == comparable(b)
      }
    }
  }

  implicit val equivFile: Equiv[File] = {
    new Equiv[File] {
      def equiv(a: File, b: File) = a.getAbsoluteFile == b.getAbsoluteFile
    }
  }

  implicit val equivOutput: Equiv[APIOutput] = {
    new Equiv[APIOutput] {
      implicit val outputGroupsOrdering =
        Ordering.by((og: OutputGroup) => og.getSourceDirectory)

      def equiv(out1: APIOutput, out2: APIOutput) = (out1, out2) match {
        case (m1: MultipleOutput, m2: MultipleOutput) =>
          (m1.getOutputGroups.length == m2.getOutputGroups.length) &&
            (m1.getOutputGroups.sorted zip m2.getOutputGroups.sorted forall {
              case (a, b) =>
                equivFile
                  .equiv(a.getSourceDirectory, b.getSourceDirectory) && equivFile
                  .equiv(a.getOutputDirectory, b.getOutputDirectory)
            })
        case (s1: SingleOutput, s2: SingleOutput) =>
          equivFile.equiv(s1.getOutputDirectory, s2.getOutputDirectory)
        case _ =>
          false
      }
    }
  }

  // for compatibility
  val equivOpts: Equiv[MiniOptions] = equivOpts0(Equiv.fromFunction(_ sameElements _))

  def equivOpts0(equivScalacOpts: Equiv[Array[String]]): Equiv[MiniOptions] = {
    new Equiv[MiniOptions] {
      def equiv(a: MiniOptions, b: MiniOptions) = {
        equivScalacOpts.equiv(a.scalacOptions, b.scalacOptions) &&
        equivJavacOptions.equiv(a.javacOptions, b.javacOptions)
      }
    }
  }

  implicit val equivCompilerVersion: Equiv[String] = {
    new Equiv[String] {
      def equiv(a: String, b: String) = a == b
    }
  }

  implicit val equivOrder: Equiv[CompileOrder] = {
    new Equiv[CompileOrder] {
      def equiv(a: CompileOrder, b: CompileOrder) = a == b
    }
  }

  def equivScalacOptions(ignoredRegexes: Array[String]): Equiv[Array[String]] = {
    equivCompilerOptions(ignoredRegexes)
  }

  // ignoring -d as it is overridden anyway
  val equivJavacOptions: Equiv[Array[String]] = equivCompilerOptions(Array("-d .*"))

  def equivCompilerOptions(ignoredRegexes: Array[String]): Equiv[Array[String]] = {
    def groupWithParams(opts: Array[String]): Set[String] = {
      def isParam(s: String) = !s.startsWith("-")
      def recur(opts: List[String], res: Set[String]): Set[String] = opts match {
        case opt :: param :: rest if isParam(param) => recur(rest, res + s"$opt $param")
        case opt :: rest                            => recur(rest, res + opt)
        case Nil                                    => res
      }
      recur(opts.toList, Set.empty)
    }
    val ignoreRegex = ignoredRegexes.mkString("|").r
    def dropIgnored(opts: Set[String]): Set[String] =
      opts.filterNot(ignoreRegex.pattern.matcher(_).matches)

    new Equiv[Array[String]] {
      override def equiv(opts1: Array[String], opts2: Array[String]): Boolean = {
        dropIgnored(groupWithParams(opts1)) == dropIgnored(groupWithParams(opts2))
      }
    }
  }
}
