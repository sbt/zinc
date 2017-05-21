/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;
import xsbti.T2;

import java.io.File;
import java.util.Optional;

/*
* This API is subject to change.
*
* It is the client's responsibility to:
*  1. Manage class loaders.  Usually the client will want to:
*    i. Keep the class loader used by the ScalaInstance warm.
*    ii. Keep the class loader of the incremental recompilation classes (xsbti.compile) warm.
*    iii. Share the class loader for Scala classes between the incremental compiler implementation and the ScalaInstance where possible (must be binary compatible)
*  2. Manage the compiler interface jar.  The interface must be compiled against the exact Scala version used for compilation and a compatible Java version.
*  3. Manage compilation order between different compilations.
*    i. Execute a compilation for each dependency, obtaining an Analysis for each.
*    ii. Provide the Analysis from previous compilations to dependent compilations in the analysis map.
*  4. Provide an implementation of JavaCompiler for compiling Java sources.
*  5. Define a function that determines if a classpath entry contains a class (Setup.definesClass).
*    i. This is provided by the client so that the client can cache this information across compilations when compiling multiple sets of sources.
*    ii. The cache should be cleared for each new compilation run or else recompilation will not properly account for changes to the classpath.
*  6. Provide a cache directory.
*    i. This directory is used by IncrementalCompiler to persist data between compilations.
*    ii. It should be a different directory for each set of sources being compiled.
*  7. Manage parallel execution.
*    i. Each compilation may be performed in a different thread as long as the dependencies have been compiled already.
*    ii. Implementations of all types should be immutable and arrays treated as immutable.
*  8. Ensure general invariants:
*    i. The implementations of all types are immutable, except for the already discussed Setup.definesClass.
*    ii. Arrays are treated as immutable.
*    iii. No value is ever null.
*/
public interface IncrementalCompiler {

    /**
     * Performs an incremental compilation given an instance of {@link Inputs}.
     *
     * @param inputs An instance of {@link Inputs} that collect all the inputs
     *               required to run the compiler (from sources and classpath,
     *               to compilation order, previous results, current setup, etc).
     * @param logger An instance of {@link Logger} that logs Zinc output.
     *
     * @return An instance of {@link CompileResult} that holds information
     * about the results of the compilation.
     */
    CompileResult compile(Inputs inputs, Logger logger);

    /**
     * Performs an incremental compilation given its configuration.
     *
     * @param scalaCompiler The Scala compiler to compile Scala sources.
     * @param javaCompiler The Java compiler to compile Java sources.
     * @param sources An array of Java and Scala source files to be compiled.
     * @param classpath An array of files representing classpath entries.
     * @param output An instance of {@link Output} to store the compiler outputs.
     * @param globalsCache Directory where previous cached compilers are stored.
     * @param scalacOptions An array of options/settings for the Scala compiler.
     * @param javacOptions An array of options for the Java compiler.
     * @param previousAnalysis Optional previous incremental compilation analysis.
     * @param previousSetup Optional previous incremental compilation setup.
     * @param perClasspathEntryLookup Lookup of data structures and operations
     *                                for a given classpath entry.
     * @param reporter An instance of {@link Reporter} to report compiler output.
     * @param compileOrder The order in which Java and Scala sources should
     *                     be compiled.
     * @param skip Flag to ignore this compilation run and return previous one.
     * @param progress An instance of {@link CompileProgress} to keep track of
     *                 the current compilation progress.
     * @param incrementalOptions An Instance of {@link IncOptions} that
     *                           configures the incremental compiler behaviour.
     * @param extra An array of sbt tuples with extra options.
     * @param logger An instance of {@link Logger} that logs Zinc output.
     *
     *
     * @return An instance of {@link CompileResult} that holds information
     * about the results of the compilation.
     */
    CompileResult compile(ScalaCompiler scalaCompiler,
                          JavaCompiler javaCompiler,
                          File[] sources,
                          File[] classpath,
                          Output output,
                          GlobalsCache globalsCache,
                          String[] scalacOptions,
                          String[] javacOptions,
                          Optional<CompileAnalysis> previousAnalysis,
                          Optional<MiniSetup> previousSetup,
                          PerClasspathEntryLookup perClasspathEntryLookup,
                          Reporter reporter,
                          CompileOrder compileOrder,
                          // Has to be boxed to override in Scala,
                          // this is a bug of the Scala compiler 2.12
                          java.lang.Boolean skip,
                          Optional<CompileProgress> progress,
                          IncOptions incrementalOptions,
                          T2<String, String>[] extra,
                          Logger logger);
}
