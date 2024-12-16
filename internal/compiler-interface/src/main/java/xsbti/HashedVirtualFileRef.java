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

package xsbti;

/**
 * <code>HashedVirtualFileRef</code> represents a virtual file reference
 * with content hash.
 */
public interface HashedVirtualFileRef extends VirtualFileRef {
  // TODO: remove this
  static HashedVirtualFileRef of(String id, String contentHashStr) {
    return new BasicHashedVirtualFileRef(id, contentHashStr, 0L);
  }

  static HashedVirtualFileRef of(String id, String contentHashStr, long sizeBytes) {
    return new BasicHashedVirtualFileRef(id, contentHashStr, sizeBytes);
  }

  String contentHashStr();
  long sizeBytes();
}
