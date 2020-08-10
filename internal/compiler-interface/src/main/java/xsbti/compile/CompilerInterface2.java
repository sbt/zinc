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

import xsbti.AnalysisCallback;
import xsbti.Logger;
import xsbti.Reporter;
import xsbti.VirtualFile;

/** Interface for running compilation.
 * An implementation is loaded using java.util.ServiceLoader.
 */
public interface CompilerInterface2 {
  void run(
    VirtualFile[] sources,
    DependencyChanges changes,
    String[] options,
    Output output,
    AnalysisCallback callback,
    Reporter delegate,
    CompileProgress progress,
    Logger log
  );
}

