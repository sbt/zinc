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

/** Scaladoc Interface as of Zinc 1.4.0. */
public interface ScaladocInterface2 {
  void run(VirtualFile[] sources, String[] args, Logger log, Reporter delegate);
}

