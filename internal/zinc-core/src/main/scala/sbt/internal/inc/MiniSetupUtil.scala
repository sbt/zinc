/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt
package internal
package inc

import java.nio.file.Path

import xsbti.{ Logger, T2 }
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
  def equivCompileSetup(logger: Logger, equivOpts: Equiv[MiniOptions])(
      implicit equivComp: Equiv[String]
  ): Equiv[MiniSetup] = {
    new Equiv[MiniSetup] {
      def equiv(a: MiniSetup, b: MiniSetup) = {
        def prettyOptions(a: MiniOptions) =
          s"scala=${a.scalacOptions.mkString(",")} java=${a.javacOptions.mkString(",")}"
        def sameOptions = {
          val ret = equivOpts.equiv(a.options, b.options)
          if (!ret)
            logger.debug { () =>
              s"""Found differing options:
                 |a: ${prettyOptions(a.options)}
                 |b: ${prettyOptions(b.options)}
                 |""".stripMargin
            }
          ret
        }
        def sameCompiler = {
          val ret = equivComp.equiv(a.compilerVersion, b.compilerVersion)
          if (!ret)
            logger.debug { () =>
              s"Found differing compiler version ${a.compilerVersion} ${b.compilerVersion}"
            }
          ret
        }
        def sameOrder = a.order == b.order
        def sameExtra = equivPairs.equiv(a.extra, b.extra)
        sameOptions && sameCompiler && sameOrder && sameExtra
      }
    }
  }

  implicit val equivPairs: Equiv[Array[T2[String, String]]] = {
    new Equiv[Array[T2[String, String]]] {
      private def comparable(
          a: Array[T2[String, String]]
      ): Set[(String, String)] = {
        a.filterNot(_.get1 startsWith "info.")
          .map(v => v.get1() -> v.get2())
          .toSet
      }

      def equiv(
          a: Array[T2[String, String]],
          b: Array[T2[String, String]]
      ): Boolean = {
        comparable(a) == comparable(b)
      }
    }
  }

  implicit val equivFile: Equiv[Path] = {
    new Equiv[Path] {
      def equiv(a: Path, b: Path) = a.toAbsolutePath == b.toAbsolutePath
    }
  }

  implicit val equivOutput: Equiv[APIOutput] = {
    new Equiv[APIOutput] {
      implicit val outputGroupsOrdering =
        Ordering.by((og: OutputGroup) => og.getSourceDirectoryAsPath)

      def equiv(out1: APIOutput, out2: APIOutput) = (out1, out2) match {
        case (m1: MultipleOutput, m2: MultipleOutput) =>
          (m1.getOutputGroups.length == m2.getOutputGroups.length) &&
          (m1.getOutputGroups.sorted zip m2.getOutputGroups.sorted forall {
            case (a, b) =>
              equivFile
                .equiv(a.getSourceDirectoryAsPath, b.getSourceDirectoryAsPath) && equivFile
                .equiv(a.getOutputDirectoryAsPath, b.getOutputDirectoryAsPath)
          })
        case (s1: SingleOutput, s2: SingleOutput) =>
          equivFile.equiv(s1.getOutputDirectoryAsPath, s2.getOutputDirectoryAsPath)
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
