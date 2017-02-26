/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
