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

import java.io.InputStream;

/**
 * <code>VirtualFile</code> is an abstraction for file-like objects.
 * Unlike <code>java.io.File</code> or <code>java.nio.file.Path</code>
 * that are tied to filesystem-related capabilities, <code>VirtualFile</code>
 * is designed to be a less-capable object with identity, content reading,
 * and content hashing.
 * See also {@link VirtualFileRef}
 *
 * <p>One of the goals of this <i>virtual</i> file (as opposed to a concrete file)
 * is to abstract from the machine and user specifics.
 * Previous Zinc's <code>Analysis</code> metadata files
 * stored file paths using <code>java.io.File</code>.
 * This impeded them from being shared across machines
 * without pre- and post-processing them appropriately.
 * </p>
 * <p>To create a <code>VirtualFile</code> you may use a {@link FileConverter},
 * such as <code>MappedFileConverter</code>.
 * <code>MappedFileConverter</code> internally stores the root paths to the working directory,
 * Coursier's cache, etc..., and it will create a <code>VirtualFile</code> with an <code>id</code>
 * that looks like <code>${BASE}/src/main/example/A.scala</code>.
 * </p>
 * <p>A <code>VirtualFile</code> can also be created with plain <code>String</code>s
 * to represent the content, without any "real" files.
 * </p>
 * <h3>OK, but how does the compiler compile these?</h3>
 * See <code>IncrementalCompiler.java</code>.
 * At the top layer of Zinc, we are passing in the source files as a
 * sequence of {@link VirtualFile}s.
 * The files then gets wrapped by a datatype called <code>AbstractZincFile</code>,
 * which extends <code>scala.reflect.io.AbstractFile</code>,
 * which the compiler is able to compile.
 */
public interface VirtualFile extends VirtualFileRef {
  long contentHash();
  InputStream input();
}
