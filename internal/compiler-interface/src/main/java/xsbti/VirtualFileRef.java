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
 * <code>VirtualFileRef</code> represents a reference to a file-like object.
 * Unlike <code>java.io.File</code> or <code>java.nio.file.Path</code>
 * that are tied to filesystem-related capabilities, <code>VirtualFileRef</code>
 * is designed to be a less-capable pointer to an object whose
 * main feature is having a path-like identity.
 * The equality is expected to be implemented using the equality
 * of the <code>id()</code> alone. See {@link BasicVirtualFileRef}.
 *
 * <p>{@link VirtualFile}, a subtype of <code>VirtualFileRef</code>
 * supports minimally viable file-like opration needed for compilation.
 * To illustrate the difference between the two, consider the
 * following flow of operation.
 * </p>
 *
 * <h3>Flow of operation</h3>
 * <p>Suppose that there are two people Alice and Bob who are working on the same repo.
 * On Alice's machine, the build tool would construct
 * an instance of {@link FileConverter} used for the entire build.
 * The reference implementation is <code>sbt.internal.inc.MappedFileConverter</code>.
 * The build tool would pass the usual suspect of absolute paths
 * to this <code>MappedFileConverter</code> such as the base directory
 * of the working directory, Coursier cache directory, etc.
 * Given the sequence of Scala and Java source files,
 * <code>MappedFileConverter</code> will create a sequence of
 * <code>MappedVirtualFile</code> whose <code>id</code> would look like
 * <code>${BASE}/src/main/example/A.scala</code>.
 * </p>
 * <p>When Alice runs the compilation, <code>Analysis</code> file
 * will store the <code>VirtualFileRef</code> represented by
 * its <code>id</code>. This extends not only to the source files,
 * but also to <code>*.class</code> products and library JAR files.
 * See <code>MRelationsNameHashing</code>.
 * </p>
 * <p>Suppose then we are able to package the <code>Analysis</code>
 * together with the <code>*.class</code> files in a JAR file,
 * Bob on a different machine can extract it, and have the same
 * <code>Analysis</code> file. The only difference would be that on
 * his machine the build tool would have created a slightly different
 * {@link FileConverter} with different base paths.
 * Because <code>${BASE}/src/main/example/A.scala</code> would still
 * be called the same, hopefully he can resume incremental compilation.
 * </p>
 *
 * <h3>Difference between VirtualFileRef and VirtualFile</h3>
 * <p><code>VirtualFileRef</code> on its own can only have identity
 * information to be compared against another.
 * At the most basic level this could be implemented as
 * {@link BasicVirtualFileRef} that has no ability of reading the content.
 * On the other hand, {@link VirtualFile} is internally able to
 * thaw itself back to the "real" file with the help of the
 * {@link FileConverter}, and thus it might even be aware of the
 * absolute paths.
 * </p>
 * <p>So when we look at the two types <code>VirtualFileRef</code>
 * and <code>VirtualFile</code>, <code>VirtualFileRef</code> could
 * represent a "path" for either the local machine or someone else's
 * machine; on the other hand, <code>VirtualFile</code> would
 * represent something more concrete, like a local file or
 * an in-memory file.
 *
 * <h3>ok, so how would the compiler compile this?</h3>
 * See <code>IncrementalCompile.scala</code>.
 * At the top layer of Zinc, we are passing in the source files as a
 * sequence of {@link VirtualFile}s.
 * The files then gets wrapped by a datatype called <code>VirtualFileWrap</code>,
 * which extends <code>scala.reflect.io.AbstractFile</code>,
 * which the compiler is able to compile.
 */
public interface VirtualFileRef {
  public static VirtualFileRef of(String id) {
      return new BasicVirtualFileRef(id);
  }

  /**
   * Returns unique identifier for the file.
   * For Java compilation it needs to contain the path
   * structure matching the package name
   * for example com/acme/Foo.java.
   *
   * It also must end with the same value as name(),
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
