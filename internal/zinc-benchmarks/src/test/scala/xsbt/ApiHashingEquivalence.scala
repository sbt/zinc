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

package xsbt

import java.nio.file.Paths

import xsbt.api._
import xsbti.api.{ ClassLike, NameHash }

/**
 * Checks that `HashAPI` and `NameHashing` agree with their legacy copies on every class of the
 * corpora written by [[ApiCorpus]].
 *
 * Usage: `ApiHashingEquivalence <corpus.ser.gz>...`
 */
object ApiHashingEquivalence {
  def main(args: Array[String]): Unit = {
    val mismatches = args.toList.map { corpus =>
      val classes = ApiCorpus.read(Paths.get(corpus))
      val mismatches = classes.toList.flatMap(mismatchesIn)
      println(s"$corpus: ${classes.length} classes, ${mismatches.size} mismatches")
      mismatches.take(20).foreach(m => println(s"  $m"))
      mismatches.size
    }
    if (mismatches.sum > 0) sys.exit(1)
  }

  // Reused across classes too, to check that a hasher's state does not leak between them.
  private val sharedNameHashing =
    Map(false -> new NameHashing(false), true -> new NameHashing(true))

  def mismatchesIn(c: ClassLike): List[String] = {
    def check[A](what: String, expected: A, actual: A): Option[String] =
      if (expected == actual) None else Some(s"${c.name}: $what $expected != $actual")
    def sorted(nameHashes: Array[NameHash]): List[(String, String, Int)] =
      nameHashes.toList.map(nh => (nh.name, nh.scope.toString, nh.hash)).sorted
    List(
      check("apiHash", LegacyHashAPI(c), HashAPI(c)),
      check(
        "extraHash",
        LegacyHashAPI(_.hashAPI(c), includePrivateDefsInTrait = true),
        HashAPI(_.hashAPI(c), includePrivateDefsInTrait = true)
      )
    ).flatten ++ List(false, true).flatMap { optimizedSealed =>
      check(
        s"nameHashes(optimizedSealed = $optimizedSealed)",
        sorted(new LegacyNameHashing(optimizedSealed).nameHashes(c)),
        sorted(new NameHashing(optimizedSealed).nameHashes(c))
      ) ++ check(
        s"shared nameHashes(optimizedSealed = $optimizedSealed)",
        sorted(new LegacyNameHashing(optimizedSealed).nameHashes(c)),
        sorted(sharedNameHashing(optimizedSealed).nameHashes(c))
      )
    }
  }
}
