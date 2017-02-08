/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Maybe;

/**
 * Helper class for xsbti.compile.IncToolOptions.
 */
public class IncToolOptionsUtil {
  public static boolean defaultUseCustomizedFileManager() {
    return false;
  }
  public static Maybe<ClassFileManager> defaultClassFileManager() {
    return Maybe.<ClassFileManager>nothing();
  }

  public static IncToolOptions defaultIncToolOptions() {
    return new IncToolOptions(defaultClassFileManager(), defaultUseCustomizedFileManager());
  }
}
