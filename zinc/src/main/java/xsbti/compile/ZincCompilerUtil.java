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

import xsbti.Logger;

import java.io.File;
import java.util.function.Supplier;

import sbt.internal.inc.ZincUtil;

/**
 * Defines a util interface to get Scala compilers and the default implementation
 * of the Scala incremental compiler if only IncrementalCompiler is required.
 */
public interface ZincCompilerUtil {
    /** @return Return the default implementation of IncrementalCompiler. */
    public static IncrementalCompiler defaultIncrementalCompiler() {
        return ZincUtil.defaultIncrementalCompiler();
    }

    /**
     * Create a Scala compiler from a {@link ScalaInstance}, the jar containing
     * the compiler interface to be used and {@link ClasspathOptions}.
     *
     * @param scalaInstance     The Scala instance to be used.
     * @param compilerBridgeJar The jar file of the compiler bridge.
     * @param classpathOptions  The options of all the classpath that the
     *                          compiler takes in.
     * @return A Scala compiler with the given configuration.
     */
    public static ScalaCompiler scalaCompiler(
        ScalaInstance scalaInstance,
        File compilerBridgeJar,
        ClasspathOptions classpathOptions
    ) {
        return ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar, classpathOptions);
    }

    /**
     * Create a Scala compiler from a {@link ScalaInstance} and the jar
     * containing the compiler interface to be used.
     *
     * @param scalaInstance     The Scala instance to be used.
     * @param compilerBridgeJar The jar file of the compiler bridge.
     * @return A Scala compiler with the given configuration.
     */
    public static ScalaCompiler scalaCompiler(
        ScalaInstance scalaInstance,
        File compilerBridgeJar
    ) {
        return ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar);
    }

    /**
     * Defines a constant {@link CompilerBridgeProvider} that returns an already compiled bridge.
     * <p>
     * This method is useful for external build tools that want full control over the retrieval
     * and compilation of the compiler bridge, as well as the Scala instance to be used.
     *
     * @param compilerBridgeJar The jar or directory of the compiled Scala bridge.
     * @return A provider that always returns the same compiled bridge.
     */
    public static CompilerBridgeProvider constantBridgeProvider(
        ScalaInstance scalaInstance,
        File compilerBridgeJar
    ) {
        return new CompilerBridgeProvider() {
            @Override
            public File fetchCompiledBridge(ScalaInstance scalaInstance, Logger logger) {
                logger.debug(new Supplier<String>() {
                    @Override
                    public String get() {
                        String bridgeName = compilerBridgeJar.getAbsolutePath();
                        return "Returning already retrieved and compiled bridge: " + bridgeName + ".";
                    }
                });
                return compilerBridgeJar;
            }

            @Override
            public ScalaInstance fetchScalaInstance(String scalaVersion, Logger logger) {
                logger.debug(new Supplier<String>() {
                    @Override
                    public String get() {
                        String instance = scalaInstance.toString();
                        return "Returning default scala instance:\n\t" + instance;
                    }
                });
                return scalaInstance;
            }
        };
    }
}
