/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;
import java.util.Optional;
import java.util.Set;

/**
 * Defines hooks that can be user-defined to modify the behaviour of
 * internal components of the incremental compiler.
 */
public interface ExternalHooks {
    /**
     * Defines an interface for a lookup mechanism.
     */
    interface Lookup {

        /**
         * Used to provide information from external tools into sbt (e.g. IDEs)
         *
         * @param previousAnalysis
         * @return None if is unable to determine what was changed, changes otherwise
         */
        Optional<Changes<File>> getChangedSources(CompileAnalysis previousAnalysis);

        /**
         * Used to provide information from external tools into sbt (e.g. IDEs)
         *
         * @param previousAnalysis
         * @return None if is unable to determine what was changed, changes otherwise
         */
        Optional<Set<File>> getChangedBinaries(CompileAnalysis previousAnalysis);

        /**
         * Used to provide information from external tools into sbt (e.g. IDEs)
         *
         * @param previousAnalysis
         * @return None if is unable to determine what was changed, changes otherwise
         */
        Optional<Set<File>> getRemovedProducts(CompileAnalysis previousAnalysis);

        /**
         * Used to provide information from external tools into sbt (e.g. IDEs)
         *
         * @return API changes
         */
        boolean shouldDoIncrementalCompilation(Set<String> changedClasses, CompileAnalysis previousAnalysis);
    }

    /**
     * Returns the implementation of a lookup mechanism to be used instead of
     * the internal lookup provided by the default implementation.
     */
    Optional<Lookup> getExternalLookup();

    /**
     * Returns the implementation of a {@link ClassFileManager} to be used
     * alongside the internal manager provided by the default implementation.
     * <p>
     * This class file manager is run after the internal
     * {@link ClassFileManager} defined in {@link IncOptions}.
     */
    Optional<ClassFileManager> getExternalClassFileManager();

    /**
     * Returns an instance of hooks that executes the external passed class file manager.
     *
     * If several class file manager are passed, they are aggregated and their execution happens
     * in the order of invocations of this method.
     *
     * @return An instance of {@link ExternalHooks} with the aggregated external class file manager.
     */
    ExternalHooks withExternalClassFileManager(ClassFileManager externalClassFileManager);

    /**
     * Returns an instance of hooks with one lookup.
     *
     * If used several times, only the last lookup instance will be used.
     *
     * @return An instance of {@link ExternalHooks} with the specified lookup.
     */
    ExternalHooks withExternalLookup(Lookup externalLookup);
}
