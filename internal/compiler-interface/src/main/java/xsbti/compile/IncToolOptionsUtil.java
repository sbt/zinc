/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Maybe;

/**
 * Define a helper for {@link IncToolOptions} that provides information on
 * default {@link ClassFileManager class file managers} used and gives
 * default incremental compilation options to the user.
 */
public class IncToolOptionsUtil {

  /**
   * Return whether the default {@link ClassFileManager} should be used or not.
   * If true, {@link IncToolOptionsUtil#defaultClassFileManager()} is used.
   *
   * @return true if the class file manager defined in
   * {@link IncToolOptionsUtil#defaultClassFileManager()} is used, false otherwise.
   */
  public static boolean defaultUseCustomizedFileManager() {
    return false;
  }

  /**
   * Return an optional default {@link ClassFileManager}.
   *
   * @return An optional default class file manager.
   */
  public static Maybe<ClassFileManager> defaultClassFileManager() {
    return Maybe.<ClassFileManager>nothing();
  }

  /**
   * Define the default options of the tooling around incremental compilation.
   *
   * @return The default incremental compilation options.
   */
  public static IncToolOptions defaultIncToolOptions() {
    return new IncToolOptions(defaultClassFileManager(),
            defaultUseCustomizedFileManager());
  }
}
