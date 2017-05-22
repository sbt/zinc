/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

/**
 * Defines a util interface to get Scala compilers and the default implementation
 * of the Scala incremental compiler if only {@link IncrementalCompiler} is required.
 */
public interface ZincCompilerUtil {
    /**
     * @return Return the default implementation of {@link IncrementalCompiler}.
     */
    public static IncrementalCompiler defaultIncrementalCompiler() {
        return sbt.internal.inc.ZincUtil.defaultIncrementalCompiler();
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
     * @see sbt.inc.ZincBridgeProvider Utility to get the Scala instance using ivy.
     */
    public static ScalaCompiler scalaCompiler(ScalaInstance scalaInstance,
                                              File compilerBridgeJar,
                                              ClasspathOptions classpathOptions) {
        return sbt.internal.inc.ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar, classpathOptions);
    }

    /**
     * Create a Scala compiler from a {@link ScalaInstance} and the jar
     * containing the compiler interface to be used.
     *
     * @param scalaInstance     The Scala instance to be used.
     * @param compilerBridgeJar The jar file of the compiler bridge.
     * @return A Scala compiler with the given configuration.
     * @see sbt.inc.ZincBridgeProvider Utility to get the Scala instance using ivy.
     */
    public static ScalaCompiler scalaCompiler(ScalaInstance scalaInstance,
                                              File compilerBridgeJar) {
        return sbt.internal.inc.ZincUtil.scalaCompiler(scalaInstance, compilerBridgeJar);
    }
}
