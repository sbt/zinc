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
 * <p>One of the goals of this <i>virtual</i> file (as opposed to concrete file)
 * is abtraction from the machine and user specifics.
 * Previous <code>Analysis</code> file that records the relationships among
 * the incremental compilation stored the file names using <code>java.io.File</code>.
 * This prevented the <code>Analysis</code> information to be shared across machines
 * without manipulating the absolute paths in the file names.
 * </p>
 * <p>To create a <code>VirtualFile</code>, one way of doing so is creating
 * an instance of {@link FileConverter}, such as <code>MappedFileConverter</code>.
 * <code>MappedFileConverter</code> internally stores the root paths to the working directory,
 * Coursier cache etc, and it will create a <code>VirtualFile</code> with an <code>id</code>
 * that looks like <code>${0}/src/main/example/A.scala</code>.
 * </p>
 * <p>Note that <code>VirtualFile</code> can also be implemented with just plain Java objects
 * using <code>String</code> to represent the content, without "real" files.
 * </p>
 * <h3>ok, so how would the compiler compile this?</h3>
 * See <code>IncrementalCompile.scala</code>.
 * At the top layer of Zinc, we are passing in the source files as a
 * sequence of {@link VirtualFile}s.
 * The files then gets wrapped by a datatype called <code>VirtualFileWrap</code>,
 * which extends <code>scala.reflect.io.AbstractFile</code>,
 * which the compiler is able to compile.
 */
public interface VirtualFile extends VirtualFileRef {
  long contentHash();
  InputStream input();
}
