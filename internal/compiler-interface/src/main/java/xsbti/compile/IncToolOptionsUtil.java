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

import java.util.Optional;

/**
 * Define a helper for {@link IncToolOptions} that provides information on
 * default {@link ClassFileManager class file managers} used and gives
 * default incremental compilation options to the user.
 *
 * The default customized classfile manager and incremental options are empty
 * options because these are disabled by default in Java compilers and Java doc.
 * {@link IncToolOptions} are only supposed to be used for the Scala incremental
 * compiler.
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
  public static Optional<ClassFileManager> defaultClassFileManager() {
    return Optional.empty();
  }

  /**
   * Define the default options of the tooling around incremental compilation.
   *
   * @return The default incremental compilation options.
   */
  public static IncToolOptions defaultIncToolOptions() {
    return new IncToolOptions(defaultClassFileManager(), defaultUseCustomizedFileManager());
  }
}
