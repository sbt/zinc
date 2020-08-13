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

/** Console Interface as of Zinc 1.2.0.
 * An implementation is loaded using java.util.ServiceLoader.
 */
public interface ConsoleInterface1 {
  void run(
      String[] args,
      String bootClasspathString,
      String classpathString,
      String initialCommands,
      String cleanupCommands,
      ClassLoader loader,
      String[] bindNames,
      Object[] bindValues,
      Logger log);

  String[] commandArguments(
      String[] args, String bootClasspathString, String classpathString, Logger log);
}

