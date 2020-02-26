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

package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;
import xsbti.VirtualFile;

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
  boolean run(VirtualFile[] sources,
              String[] options,
              Output output,
              IncToolOptions incToolOptions,
              Reporter reporter,
              Logger log);
}
