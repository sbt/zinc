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
 * An interface to the toolchain of Java.
 *
 * Specifically, access to run javadoc + javac.
 */
public interface JavaTools {
  /** The raw interface of the java compiler for direct access. */
  JavaCompiler javac();

  /** The raw interface of the javadoc for direct access. */
  Javadoc javadoc();
}
