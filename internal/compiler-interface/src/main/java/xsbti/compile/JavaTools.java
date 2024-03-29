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

package xsbti.compile;

/**
 * Represent an interface of the toolchain of Java compilation that gives
 * access javadoc generation and Java compilation.
 */
public interface JavaTools {
  /** Return an implementation of the Java compiler (javac). */
  JavaCompiler javac();

  /** Return an implementation of a Javadoc generator. */
  Javadoc javadoc();
}
