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

import xsbti.VirtualFile;
import java.util.Optional;

/**
 * Define the interface to look up mapped data structures and query classpath
 * entries This interface gives answers to classpath-related questions like:
 *
 * - Is this class defined in this classpath entry?
 *
 * This interface also allows you to get the relation between a given
 * classpath entry and its existing {@link CompileAnalysis} instance.
 *
 * The classpath entry can be either a JAR file or a given directory,
 * as per the Java Language Specification.
 */
public interface PerClasspathEntryLookup {

    /**
     * Provide the {@link CompileAnalysis} mapped to a given classpath entry.
     *
     * @return An optional instance of {@link CompileAnalysis}.
     */
    Optional<CompileAnalysis> analysis(VirtualFile classpathEntry);

    /**
     * Provide an instance of {@link DefinesClass} that will allow you to
     * check whether a given classpath entry contains a binary class name.
     *
     * @return Instance of {@link DefinesClass} that will allow you to query
     *         information for a given classpath entry.
     */
    DefinesClass definesClass(VirtualFile classpathEntry);
}
