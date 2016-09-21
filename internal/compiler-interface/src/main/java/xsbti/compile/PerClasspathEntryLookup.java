package xsbti.compile;

import java.io.File;
import xsbti.Maybe;

/**
 * Defines lookup of data structures and operations zinc needs to perform on per classpath element basis.
 */
public interface PerClasspathEntryLookup {
    /** Provides the Analysis for the given classFile. */
    Maybe<CompileAnalysis> lookupAnalysis(File classFile);

    /** Provides the Analysis for the given binary class name in an external JAR. */
    Maybe<CompileAnalysis> lookupAnalysis(File binaryDependency, String binaryClassName);

    /** Provides the Analysis for the given binary class name. */
    Maybe<CompileAnalysis> lookupAnalysis(String binaryClassName);

    /**
     * Provides a function to determine if classpath entry `file` contains a given class.
     * The returned function should generally cache information about `file`, such as the list of entries in a jar.
     */
    DefinesClass definesClass(File classpathEntry);
}
