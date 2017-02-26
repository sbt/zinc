/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

import java.io.File;

/**
 * Represent a bare metal interface around one of the java tools, either
 * the Java compiler (javac) or the Javadoc generator.
 *
 * The main purpose of this interface is to abstract over the local invocation
 * of the Java toolchain and forked invocation via process.
 */
public interface JavaTool {
  /**
   * Run a concrete Java tool (either Java compiler or Javadoc generator).
   *
   * @param sources The list of Java source files to compile.
   * @param options The set of all the options to pass to the java compiler.
   *                These options also include JVM options like the classpath.
   * @param incToolOptions The set of options to pass to the Java compiler
   *                       for the incremental dependency analysis.
   * @param reporter The reporter for semantic error messages.
   * @param log      The logger to dump output into.
   *
   * @return true if no errors, false otherwise.
   */
  boolean run(File[] sources,
              String[] options,
              IncToolOptions incToolOptions,
              Reporter reporter,
              Logger log);
}
