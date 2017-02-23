package xsbti.compile;

import xsbti.*;

import java.io.File;

public interface IncrementalCompilerUtil {
    /**
     * Return the default instance of {@link }
     * @return
     */
    IncrementalCompiler defaultIncrementalCompiler();

    /**
     * Create an instance of {@link Inputs} from instances of the internal
     * Zinc API to run {@link IncrementalCompiler#compile(Inputs, Logger)}.
     *
     * @param compileOptions An instance of {@link CompileOptions} containing
     *                       input information (e.g. sources, classpaths, etc).
     * @param compilers An instance of {@link Compilers} that include both
     *                  Java and Scala compilers.
     * @param setup An instance of {@link Setup} that configures incremental
     *              compilation for both Java and Scala compilers.
     * @param previousResult An instance of {@link PreviousResult} that includes
     *                       information about last incremental compilation run.
     *
     * @return An instance of {@link Inputs}.
     */
    Inputs inputs(CompileOptions compileOptions,
                  Compilers compilers,
                  Setup setup,
                  PreviousResult previousResult);

    /**
     * Create an instance of {@link Inputs} by passing general compiler options
     * as parameters to run {@link IncrementalCompiler#compile(Inputs, Logger) compile}.
     *
     * @param classpath An array of files representing classpath entries.
     * @param sources An array of Java and Scala source files to be compiled.
     * @param classesDirectory A file where the classfiles should be stored.
     * @param scalacOptions An array of options/settings for the Scala compiler.
     * @param javacOptions An array of options for the Java compiler.
     * @param maxErrors The maximum number of errors that will be reported.
     * @param sourcePositionMappers An array of sbt functions that take a
     *                              position and maps it to a source position.
     * @param compileOrder The order in which Java and Scala sources should
     *                     be compiled.
     * @param compilers An instance of {@link Compilers} that include both
     *                  Java and Scala compilers.
     * @param setup An instance of {@link Setup} that configures incremental
     *              compilation for both Java and Scala compilers.
     * @param previousResult An instance of {@link PreviousResult} that includes
     *                       information about last incremental compilation run.
     *
     * @return An instance of {@link Inputs} usable to run .
     */
    Inputs inputs(File[] classpath,
                  File[] sources,
                  File classesDirectory,
                  String[] scalacOptions,
                  String[] javacOptions,
                  int maxErrors,
                  F1<Position, Maybe<Position>>[] sourcePositionMappers,
                  CompileOrder compileOrder,
                  Compilers compilers,
                  Setup setup,
                  PreviousResult previousResult);

    /**
     * Create an instance of {@link Setup}, useful to create an instance of
     * {@link Inputs}.
     *
     * @param perClasspathEntryLookup Lookup of data structures and operations
     *                                for a given classpath entry.
     * @param skip Flag to ignore this compilation run and return previous one.
     * @param cacheFile Cache directory for the incremental compiler.
     * @param globalsCache Directory where previous cached compilers are stored.
     * @param incrementalOptions An Instance of {@link IncOptions} that
     *                           configures the incremental compiler behaviour.
     * @param reporter An instance of {@link Reporter} to report compiler output.
     * @param progress An instance of {@link CompileProgress} to keep track of
     *                 the current compilation progress.
     * @param extra An array of sbt tuples with extra options.
     *
     * @return A {@link Setup} instance, useful to create {@link Inputs}.
     */
    Setup setup(PerClasspathEntryLookup perClasspathEntryLookup,
                boolean skip,
                File cacheFile,
                GlobalsCache globalsCache,
                IncOptions incrementalOptions,
                Reporter reporter,
                Maybe<CompileProgress> progress,
                T2<String, String>[] extra);

    /**
     * Create a Scala compiler from a {@link ScalaInstance}, the jar defining
     * the compiler interface to be used and {@link ClasspathOptions}.
     *
     * @param scalaInstance The Scala instance to be used.
     * @param compilerInterfaceJar The jar file of the compiler interface.
     * @param classpathOptions The options of all the classpath that the
     *                         compiler takes in.
     *
     * @return A Scala compiler with the given configuration.
     */
    ScalaCompiler scalaCompiler(ScalaInstance scalaInstance,
                                File compilerInterfaceJar,
                                ClasspathOptions classpathOptions);

    /**
     * Compile the compiler interface for a Scala version from concrete sources.
     *
     * This is necessary to run {@link this#scalaCompiler(ScalaInstance, File, ClasspathOptions)}
     * to create a {@link ScalaCompiler} instance that can be used for
     * incremental compilation.
     *
     * It is the client's responsability to manage compiled jars for different
     * Scala versions.
     *
     * @param label A brief name describing the source component for use in error messages
     * @param sourceJar The jar file containing the implementation of the compiler interface.
     * @param targetJar The directory to store the compiled class files.
     * @param interfaceJar The jar file that defines the compiler interface.
     * @param instance The ScalaInstance to compile the compiler interface for.
     * @param log The logger to use during compilation.
     */
    void compileScalaBridge(String label,
                            File sourceJar,
                            File targetJar,
                            File interfaceJar,
                            ScalaInstance instance,
                            Logger log);
}
