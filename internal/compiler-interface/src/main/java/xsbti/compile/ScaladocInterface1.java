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

import xsbti.Logger;
import xsbti.Reporter;

/** Scaladoc Interface as of Zinc 1.2.0.
 * An implementation is loaded using java.util.ServiceLoader.
 */
public interface ScaladocInterface1 {
  void run(String[] args, Logger log, Reporter delegate);
}
