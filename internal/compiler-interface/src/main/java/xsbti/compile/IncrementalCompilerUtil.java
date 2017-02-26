package xsbti.compile;

import java.io.File;

public interface IncrementalCompilerUtil {
    /**
     * Return the default instance of {@link }
     * @return
     */
    IncrementalCompiler defaultIncrementalCompiler();


    /**
     * Create a Scala compiler from a {@link ScalaInstance}, the jar containing
     * the compiler interface to be used and {@link ClasspathOptions}.
     *
     * @param scalaInstance The Scala instance to be used.
     * @param compilerBridgeJar The jar file of the compiler bridge.
     * @param classpathOptions The options of all the classpath that the
     *                         compiler takes in.
     *
     * @return A Scala compiler with the given configuration.
     */
    ScalaCompiler scalaCompiler(ScalaInstance scalaInstance,
                                File compilerBridgeJar,
                                ClasspathOptions classpathOptions);

    /**
     * Create a Scala compiler from a {@link ScalaInstance} and the jar
     * containing the compiler interface to be used.
     *
     * @param scalaInstance The Scala instance to be used.
     * @param compilerBridgeJar The jar file of the compiler bridge.
     *
     * @return A Scala compiler with the given configuration.
     */
    ScalaCompiler scalaCompiler(ScalaInstance scalaInstance,
                                File compilerBridgeJar);

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
    /*void compileScalaBridge(String label,
                            File sourceJar,
                            File targetJar,
                            File interfaceJar,
                            ScalaInstance instance,
                            Logger log);*/
}
