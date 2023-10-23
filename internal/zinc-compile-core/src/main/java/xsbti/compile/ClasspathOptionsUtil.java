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

/**
 * Abstract over the creation of `ClasspathOptions` by providing methods
 * that create typical classpath options based on the desired use-case.
 */
public interface ClasspathOptionsUtil {
    /**
     * Define a manual {@link ClasspathOptions} where the client manages everything.
     */
    public static ClasspathOptions manual() {
        return ClasspathOptions.of(false, false, false, true, false);
    }

    /**
     * Define boot {@link ClasspathOptions} where:
     * 1. the Scala standard library is present in the classpath;
     * 2. the boot classpath is automatically configured; and,
     * 3. the Scala standard library JAR is fetched from the classpath.
     */
    public static ClasspathOptions boot() {
        return ClasspathOptions.of(true, false, false, true, true);
    }

    /**
     * Define {@link ClasspathOptions} where the Scala standard library is present in the classpath.
     */
    public static ClasspathOptions noboot(String scalaVersion) {
        if (scalaVersion.startsWith("3.") || scalaVersion.startsWith("2.13."))
            return ClasspathOptions.of(false, false, false, false, false);
        else
            return boot();
    }

    /**
     * Define auto {@link ClasspathOptions} where:
     * 1. the Scala standard library is present in the classpath;
     * 2. the Compiler JAR is present in the classpath;
     * 3. the extra JARs present in the Scala instance are added to the classpath.
     * 4. the boot classpath is automatically configured; and,
     * 5. the Scala standard library JAR is fetched from the classpath.
     */
    public static ClasspathOptions auto() {
        return ClasspathOptions.of(true, true, true, true, true);
    }

    /**
     * Define auto {@link ClasspathOptions} where:
     * 1. the Scala standard library is present in the classpath;
     * 2. the Compiler JAR is present in the classpath;
     * 3. the extra JARs present in the Scala instance are added to the classpath.
     */
    public static ClasspathOptions autoNoboot(String scalaVersion) {
        if (scalaVersion.startsWith("3.") || scalaVersion.startsWith("2.13."))
            return ClasspathOptions.of(false, true, true, false, false);
        else
            return auto();
    }

    /**
     * Define javac {@link ClasspathOptions} where the Compiler JAR may or may not
     * be present in the classpath. Note that the classpath won't be
     * automatically configured by the underlying implementation.
     *
     * @param compilerInClasspath Whether the Scala compiler is in the classpath.
     */

    public static ClasspathOptions javac(Boolean compilerInClasspath) {
        return ClasspathOptions.of(false, compilerInClasspath, false,false,false);
    }

    /**
     * Define repl {@link ClasspathOptions} where:
     * 1. the Scala standard library is present in the classpath;
     * 2. the Compiler JAR is present in the classpath;
     * 3. the extra JARs present in the Scala instance are added to the classpath.
     * 4. the boot classpath is automatically configured; and,
     * 5. the Scala standard library JAR is fetched from the classpath.
     */
    public static ClasspathOptions repl() {
        return auto();
    }

    /**
     * Define repl {@link ClasspathOptions} where:
     * 1. the Scala standard library is present in the classpath;
     * 2. the Compiler JAR is present in the classpath;
     * 3. the extra JARs present in the Scala instance are added to the classpath.
     */
    public static ClasspathOptions replNoboot(String scalaVersion) {
        return autoNoboot(scalaVersion);
    }
}
