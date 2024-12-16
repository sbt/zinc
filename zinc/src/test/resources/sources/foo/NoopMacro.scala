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

package foo

import scala.reflect.macros.blackbox.Context

class NoopMacro(val c: Context) {
  def noop(arg: c.Tree): c.Tree = arg
}
