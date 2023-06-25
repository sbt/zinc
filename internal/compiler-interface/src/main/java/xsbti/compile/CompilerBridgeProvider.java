/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbti.compile;

import xsbti.Logger;
import java.io.File;

/**
 * Defines an interface for users to get the compiler bridge for a given Scala version.
 * <p>
 * The implementors of this interface will retrieve the compiler bridge following different
 * mechanisms. For example, sbt implements this to use LM to resolve the sources for a given Scala
 * version, which Zinc then compiles and defines an sbt component, which is reused across different
 * sbt projects.
 */
public interface CompilerBridgeProvider {
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
