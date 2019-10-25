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

/*
 * Represents a reference to a file-like object.
 */
public interface VirtualFileRef {
  public static VirtualFileRef of(String id) {
      return new BasicVirtualFileRef(id);
  }

  /**
   * Returns unique identifier for the file.
   * For Java compilation
   * it needs to contain the path structure matching
   * the package name.
   *
   * However, it must end with the same value as name(),
   * Java files must end with ".java",
   * and Scala files must end with ".scala".
   */
  public String id();

  /**
   * Returns "file name" for the file.
   * Java files must end with ".java",
   * and Scala files must end with ".scala".
   */
  public String name();

  /*
   * Returns "file name" for the file.
   * Java files must end with ".java",
   * and Scala files must end with ".scala".
   */
  public String[] names();
}
