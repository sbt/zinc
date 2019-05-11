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

import foo.NoopMacro // this import pull nonexisting object based on class NoopMacro
import scala.language.experimental.macros

class NoopMacroUsed {
  def noop(arg: Int): Int = macro NoopMacro.noop
}