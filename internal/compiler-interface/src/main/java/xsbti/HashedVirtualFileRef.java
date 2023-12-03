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

/**
 * <code>HashedVirtualFileRef</code> represents a virtual file reference
 * with content hash.
 */
public interface HashedVirtualFileRef extends VirtualFileRef {
  static HashedVirtualFileRef of(String id, String contentHashStr) {
    return new BasicHashedVirtualFileRef(id, contentHashStr);
  }

  String contentHashStr();
}
