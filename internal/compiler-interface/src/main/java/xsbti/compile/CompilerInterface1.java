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

import java.io.File;
import xsbti.AnalysisCallback;
import xsbti.Logger;
import xsbti.Reporter;
import xsbti.VirtualFile;

/** Compiler interface as of Zinc 1.2.0. */
public interface CompilerInterface1 {
  CachedCompiler newCompiler(
    String[] options,
    Output output,
    Logger initialLog,
    Reporter initialDelegate
  );

  void run(
    File[] sources,
    DependencyChanges changes,
    AnalysisCallback callback,
    Logger log,
    Reporter delegate,
    CompileProgress progress,
    CachedCompiler cached
  );
}

