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
  MiniSetup,
  CompileOrder,
  Output => APIOutput,
  SingleOutput,
  MultipleOutput,
  MiniOptions
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
  implicit def equivCompileSetup(
    implicit
    equivOutput: Equiv[APIOutput],
    equivOpts: Equiv[MiniOptions],
    equivComp: Equiv[String]
  ): Equiv[MiniSetup] = {
    new Equiv[MiniSetup] {
      def equiv(a: MiniSetup, b: MiniSetup) = {
        /* Hard-code these to use the `Equiv` defined in this class. For
         * some reason, `Equiv[Nothing]` or an equivalent is getting injected
         * into here now, and it's borking all our results. This fixes it. */
        def sameOutput = MiniSetupUtil.equivOutput.equiv(a.output, b.output)
        def sameOptions = MiniSetupUtil.equivOpts.equiv(a.options, b.options)
        def sameCompiler = equivComp.equiv(a.compilerVersion, b.compilerVersion)
        def sameOrder = a.order == b.order
        def sameNameHasher = a.nameHashing == b.nameHashing
        def sameExtra = equivPairs.equiv(a.extra, b.extra)

        sameOutput &&
          sameOptions &&
          sameCompiler &&
          sameOrder &&
          sameNameHasher &&
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
        Ordering.by((og: MultipleOutput.OutputGroup) => og.sourceDirectory)

      def equiv(out1: APIOutput, out2: APIOutput) = (out1, out2) match {
        case (m1: MultipleOutput, m2: MultipleOutput) =>
          (m1.outputGroups.length == m2.outputGroups.length) &&
            (m1.outputGroups.sorted zip m2.outputGroups.sorted forall {
              case (a, b) =>
                equivFile
                  .equiv(a.sourceDirectory, b.sourceDirectory) && equivFile
                  .equiv(a.outputDirectory, b.outputDirectory)
            })
        case (s1: SingleOutput, s2: SingleOutput) =>
          equivFile.equiv(s1.outputDirectory, s2.outputDirectory)
        case _ =>
          false
      }
    }
  }

  implicit val equivOpts: Equiv[MiniOptions] = {
    new Equiv[MiniOptions] {
      def equiv(a: MiniOptions, b: MiniOptions) = {
        (a.scalacOptions sameElements b.scalacOptions) &&
          (a.javacOptions sameElements b.javacOptions)
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
}
