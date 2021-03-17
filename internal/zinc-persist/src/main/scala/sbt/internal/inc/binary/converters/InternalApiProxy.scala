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

package sbt.internal.inc.binary.converters

/**
 * Defines a proxy to the Java compiler interface to create different utils.
 *
 * This proxy is required for an efficient deserialization of the analysis files.
 * It exposes implementation details and uses reflection methods to access private
 * constructors.
 *
 * This proxy is not public, Do not depend on it, it has no binary compatibility
 * guarantee and can be broken in any minor release.
 */
object InternalApiProxy {
  object Modifiers {
    def apply(flags: Int): xsbti.api.Modifiers = {
      val constructor = classOf[xsbti.api.Modifiers].getDeclaredConstructor(java.lang.Byte.TYPE)
      constructor.setAccessible(true)
      constructor.newInstance(flags.toByte: java.lang.Byte)
    }
  }
}
