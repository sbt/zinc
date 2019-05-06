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

package xsbti;

public interface InteractiveConsoleFactory {
  InteractiveConsoleInterface createConsole(
      String[] args,
      String bootClasspathString,
      String classpathString,
      String initialCommands,
      String cleanupCommands,
      ClassLoader loader,
      String[] bindNames,
      Object[] bindValues,
      Logger log
  );
}
