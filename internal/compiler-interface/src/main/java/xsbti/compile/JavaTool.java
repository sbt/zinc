/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;
import xsbti.Logger;
import xsbti.Reporter;

/**
 * JavaTool represents a "bare metal" interface around one of the java tools:
 * the Java compiler and javadoc.
 * Instead of taking sbt-specific data structures for the arguments,
 * it takes an array of raw string for the options.
 *
 * The main purpose of this interface is to abstract over the local invocation
 * of the Java toolchain and forked invocation via process.
 * See also sbt.internal.inc.javac.JavaTools, sbt.internal.inc.javac.JavaCompiler,
 * and sbt.internal.inc.javac.Javadoc.
 */
public interface JavaTool {
  /**
   * Runs the tool such as javac or javadoc.
   *
   * @param sources  The list of java source files to compile.
   * @param options  The set of options to pass to the java compiler (includes the classpath).
   * @param incToolOptions The set of options to pass to the java compiler for incremental compile purpose.
   * @param reporter The reporter for semantic error messages.
   * @param log      The logger to dump output into.
   * @return true if no errors, false otherwise.
   */
  boolean run(File[] sources, String[] options, IncToolOptions incToolOptions,
              Reporter reporter, Logger log);
}
