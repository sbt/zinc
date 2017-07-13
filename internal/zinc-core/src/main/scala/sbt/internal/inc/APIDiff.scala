/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbt.api.DefaultShowAPI
import java.lang.reflect.Method
import java.util.{ List => JList }

import xsbti.api.Companions

/**
 * A class which computes diffs (unified diffs) between two textual representations of an API.
 *
 */
private[inc] class APIDiff {

  /**
   * Generates an unified diff between textual representations of `api1` and `api2`.
   */
  def generateApiDiff(fileName: String,
                      api1: Companions,
                      api2: Companions,
                      contextSize: Int): String = {
    val api1Str = DefaultShowAPI(api1.classApi) + "\n" + DefaultShowAPI(api1.objectApi)
    val api2Str = DefaultShowAPI(api2.classApi) + "\n" + DefaultShowAPI(api2.objectApi)
    DiffUtil.mkColoredCodeDiff(api1Str, api2Str, printDiffDel = true)
  }

  /**
   * This class was directly copied from Dotty
   * [[https://github.com/lampepfl/dotty/blob/0.1.2-RC1/compiler/src/dotty/tools/dotc/util/DiffUtil.scala]]
   *
   * Copyright (c) 2014, EPFL
   *
   * All rights reserved.
   * Redistribution and use in source and binary forms, with or without modification, are
   * permitted provided that the following conditions are met:
   *
   * 1. Redistributions of source code must retain the above copyright notice, this list of
   * conditions and the following disclaimer.
   * 2. Redistributions in binary form must reproduce the above copyright notice, this list
   * of conditions and the following disclaimer in the documentation and/or other materials
   * provided with the distribution.
   * 3. Neither the name of the copyright holder nor the names of its contributors may be used
   * to endorse or promote products derived from this software without specific prior written
   * permission.
   *
   * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
   * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
   * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
   * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
   * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
   * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
   * OF THE POSSIBILITY OF SUCH DAMAGE.
   */
  private object DiffUtil {

    import scala.annotation.tailrec
    import scala.collection.mutable

    private final val ANSI_DEFAULT = "\u001B[0m"
    private final val ANSI_RED = "\u001B[31m"
    private final val ANSI_GREEN = "\u001B[32m"

    private final val DELETION_COLOR = ANSI_RED
    private final val ADDITION_COLOR = ANSI_GREEN

    @tailrec private def splitTokens(str: String, acc: List[String] = Nil): List[String] = {
      if (str == "") {
        acc.reverse
      } else {
        val head = str.charAt(0)
        val (token, rest) = if (Character.isAlphabetic(head) || Character.isDigit(head)) {
          str.span(c => Character.isAlphabetic(c) || Character.isDigit(c))
        } else if (Character.isMirrored(head) || Character.isWhitespace(head)) {
          str.splitAt(1)
        } else {
          str.span { c =>
            !Character.isAlphabetic(c) && !Character.isDigit(c) &&
            !Character.isMirrored(c) && !Character.isWhitespace(c)
          }
        }
        splitTokens(rest, token :: acc)
      }
    }

    /** @return a tuple of the (found, expected, changedPercentage) diffs as strings */
    def mkColoredTypeDiff(found: String, expected: String): (String, String, Double) = {
      var totalChange = 0
      val foundTokens = splitTokens(found, Nil).toArray
      val expectedTokens = splitTokens(expected, Nil).toArray

      val diffExp = hirschberg(foundTokens, expectedTokens)
      val diffAct = hirschberg(expectedTokens, foundTokens)

      val exp = diffExp.collect {
        case Unmodified(str) => str
        case Inserted(str) =>
          totalChange += str.length
          ADDITION_COLOR + str + ANSI_DEFAULT
      }.mkString

      val fnd = diffAct.collect {
        case Unmodified(str) => str
        case Inserted(str) =>
          totalChange += str.length
          DELETION_COLOR + str + ANSI_DEFAULT
      }.mkString

      (fnd, exp, totalChange.toDouble / (expected.length + found.length))
    }

    def mkColoredLineDiff(expected: String, actual: String): String = {
      val tokens = splitTokens(expected, Nil).toArray
      val lastTokens = splitTokens(actual, Nil).toArray

      val diff = hirschberg(lastTokens, tokens)

      "  |SOF\n" + diff.collect {
        case Unmodified(str) =>
          "  |" + str
        case Inserted(str) =>
          ADDITION_COLOR + "e |" + str + ANSI_DEFAULT
        case Modified(old, str) =>
          DELETION_COLOR + "a |" + old + "\ne |" + ADDITION_COLOR + str + ANSI_DEFAULT
        case Deleted(str) =>
          DELETION_COLOR + "\na |" + str + ANSI_DEFAULT
      }.mkString + "\n  |EOF"
    }

    def mkColoredCodeDiff(code: String, lastCode: String, printDiffDel: Boolean): String = {
      val tokens = splitTokens(code, Nil).toArray
      val lastTokens = splitTokens(lastCode, Nil).toArray

      val diff = hirschberg(lastTokens, tokens)

      diff.collect {
        case Unmodified(str) => str
        case Inserted(str)   => ADDITION_COLOR + str + ANSI_DEFAULT
        case Modified(old, str) if printDiffDel =>
          DELETION_COLOR + old + ADDITION_COLOR + str + ANSI_DEFAULT
        case Modified(_, str)             => ADDITION_COLOR + str + ANSI_DEFAULT
        case Deleted(str) if printDiffDel => DELETION_COLOR + str + ANSI_DEFAULT
      }.mkString
    }

    private sealed trait Patch
    private final case class Unmodified(str: String) extends Patch
    private final case class Modified(original: String, str: String) extends Patch
    private final case class Deleted(str: String) extends Patch
    private final case class Inserted(str: String) extends Patch

    private def hirschberg(a: Array[String], b: Array[String]): Array[Patch] = {
      def build(x: Array[String], y: Array[String], builder: mutable.ArrayBuilder[Patch]): Unit = {
        if (x.isEmpty) {
          builder += Inserted(y.mkString)
        } else if (y.isEmpty) {
          builder += Deleted(x.mkString)
        } else if (x.length == 1 || y.length == 1) {
          needlemanWunsch(x, y, builder)
        } else {
          val xlen = x.length
          val xmid = xlen / 2
          val ylen = y.length

          val (x1, x2) = x.splitAt(xmid)
          val leftScore = nwScore(x1, y)
          val rightScore = nwScore(x2.reverse, y.reverse)
          val scoreSum = (leftScore zip rightScore.reverse).map {
            case (left, right) => left + right
          }
          val max = scoreSum.max
          val ymid = scoreSum.indexOf(max)

          val (y1, y2) = y.splitAt(ymid)
          build(x1, y1, builder)
          build(x2, y2, builder)
        }
      }
      val builder = Array.newBuilder[Patch]
      build(a, b, builder)
      builder.result()
    }

    private def nwScore(x: Array[String], y: Array[String]): Array[Int] = {
      def ins(s: String) = -2
      def del(s: String) = -2
      def sub(s1: String, s2: String) = if (s1 == s2) 2 else -1

      val score = Array.fill(x.length + 1, y.length + 1)(0)
      for (j <- 1 to y.length)
        score(0)(j) = score(0)(j - 1) + ins(y(j - 1))
      for (i <- 1 to x.length) {
        score(i)(0) = score(i - 1)(0) + del(x(i - 1))
        for (j <- 1 to y.length) {
          val scoreSub = score(i - 1)(j - 1) + sub(x(i - 1), y(j - 1))
          val scoreDel = score(i - 1)(j) + del(x(i - 1))
          val scoreIns = score(i)(j - 1) + ins(y(j - 1))
          score(i)(j) = scoreSub max scoreDel max scoreIns
        }
      }
      Array.tabulate(y.length + 1)(j => score(x.length)(j))
    }

    private def needlemanWunsch(x: Array[String],
                                y: Array[String],
                                builder: mutable.ArrayBuilder[Patch]): Unit = {
      def similarity(a: String, b: String) = if (a == b) 2 else -1
      val d = 1
      val score = Array.tabulate(x.length + 1, y.length + 1) { (i, j) =>
        if (i == 0) d * j
        else if (j == 0) d * i
        else 0
      }
      for (i <- 1 to x.length) {
        for (j <- 1 to y.length) {
          val mtch = score(i - 1)(j - 1) + similarity(x(i - 1), y(j - 1))
          val delete = score(i - 1)(j) + d
          val insert = score(i)(j - 1) + d
          score(i)(j) = mtch max insert max delete
        }
      }

      var alignment = List.empty[Patch]
      var i = x.length
      var j = y.length
      while (i > 0 || j > 0) {
        if (i > 0 && j > 0 && score(i)(j) == score(i - 1)(j - 1) + similarity(x(i - 1), y(j - 1))) {
          val newHead =
            if (x(i - 1) == y(j - 1)) Unmodified(x(i - 1))
            else Modified(x(i - 1), y(j - 1))
          alignment = newHead :: alignment
          i = i - 1
          j = j - 1
        } else if (i > 0 && score(i)(j) == score(i - 1)(j) + d) {
          alignment = Deleted(x(i - 1)) :: alignment
          i = i - 1
        } else {
          alignment = Inserted(y(j - 1)) :: alignment
          j = j - 1
        }
      }
      builder ++= alignment
    }

  }

}
