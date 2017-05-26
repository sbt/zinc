/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.F0;
import xsbti.Logger;

import java.io.File;

/**
 * Defines an interface for users to get the compiler bridge for a given Scala version.
 * <p>
 * The implementors of this interface will retrieve the compiler bridge following different
 * mechanisms. By default, Zinc uses ivy to resolve the sources for a given Scala version,
 * compile them and then define the sbt component, which is reused across different sbt projects.
 */
public interface CompilerBridgeProvider {

    /**
     * Defines a constant {@link CompilerBridgeProvider} that returns an already compiled bridge.
     * <p>
     * This method is useful for external build tools that want full control over the retrieval
     * and compilation of the compiler bridge, as well as the Scala instance to be used.
     *
     * @param file The jar or directory of the compiled Scala bridge.
     * @return A provider that always returns the same compiled bridge.
     */
    static CompilerBridgeProvider constant(File file, ScalaInstance scalaInstance) {
        return new CompilerBridgeProvider() {
            @Override
            public File fetchCompiledBridge(ScalaInstance scalaInstance, Logger logger) {
                logger.debug(new F0<String>() {
                    @Override
                    public String apply() {
                        String bridgeName = file.getAbsolutePath();
                        return "Returning already retrieved and compiled bridge: " + bridgeName + ".";
                    }
                });
                return file;
            }

            @Override
            public ScalaInstance fetchScalaInstance(String scalaVersion, Logger logger) {
                logger.debug(new F0<String>() {
                    @Override
                    public String apply() {
                        String instance = scalaInstance.toString();
                        return "Returning default scala instance:\n\t" + instance;
                    }
                });
                return scalaInstance;
            }
        };
    }

    /**
     * Get the location of the compiled Scala compiler bridge for a concrete Scala version.
     *
     * @param scalaInstance The Scala instance for which the bridge should be compiled for.
     * @param logger        A logger.
     * @return The jar or directory where the bridge sources have been compiled.
     */
    File fetchCompiledBridge(ScalaInstance scalaInstance, Logger logger);

    /**
     * Get the Scala instance for a given Scala version.
     *
     * @param scalaVersion The scala version we want the instance for.
     * @param logger       A logger.
     * @return A scala instance, useful to get a compiled bridge.
     * @see ScalaInstance
     * @see CompilerBridgeProvider#fetchCompiledBridge
     */
    ScalaInstance fetchScalaInstance(String scalaVersion, Logger logger);
}
