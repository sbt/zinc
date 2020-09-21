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

package xsbti.api

/**
 * Defines a proxy to the Java compiler interface to create different utils.
 *
 * This proxy is required for an efficient deserialization of the analysis files.
 * It exposes implementation details and uses protected methods to create new
 * instances of other classes.
 *
 * Even though this proxy is public, Do not depend on it, it has no binary compatibility
 * guarantee and can be broken in any minor release.
 */
object InternalApiProxy {

  object Modifiers {
    def apply(flags: Int): Modifiers = new Modifiers(flags.toByte)
  }

}
