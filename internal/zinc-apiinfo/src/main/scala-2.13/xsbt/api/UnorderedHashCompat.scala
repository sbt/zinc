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

package xsbt.api

/** How `MurmurHash3.unorderedHash` of Scala 2.13 folds an element hash into the product. */
private[api] object UnorderedHashCompat {
  @inline def product(c: Int, h: Int): Int = c * (h | 1)
}
