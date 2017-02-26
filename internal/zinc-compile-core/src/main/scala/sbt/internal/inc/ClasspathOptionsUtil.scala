/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import xsbti.compile.ClasspathOptions

/**
 * Abstract over the creation of [[ClasspathOptions]] by providing methods
 * that create typical classpath options based on the desired use-case.
 */
class ClasspathOptionsUtil {
  /** Define [[ClasspathOptions]] where the client manages everything. */
  def manual = new ClasspathOptions(false, false, false, true, false)

  /**
   * Define [[ClasspathOptions]] where:
   *   1. the Scala standard library is present in the classpath;
   *   2. the boot classpath is automatically configured; and,
   *   3. the Scala standard library JAR is fetched from the classpath.
   */
  def boot = new ClasspathOptions(true, false, false, true, true)

  /**
   * Define [[ClasspathOptions]] where:
   *   1. the Scala standard library is present in the classpath;
   *   2. the Compiler JAR is present in the classpath;
   *   3. the extra JARs present in the Scala instance are added to the classpath.
   *   4. the boot classpath is automatically configured; and,
   *   5. the Scala standard library JAR is fetched from the classpath.
   */
  def auto = new ClasspathOptions(true, true, true, true, true)

  /**
   * Define [[ClasspathOptions]] where the Compiler JAR may or may not
   * be present in the classpath. Note that the classpath won't be
   * automatically configured by the underlying implementation.
   * @param compiler Whether the Scala compiler is in the classpath.
   */
  def javac(compiler: Boolean) = new ClasspathOptions(false, compiler, false, false, false)

  /**
   * Define [[ClasspathOptions]] where:
   *   1. the Scala standard library is present in the classpath;
   *   2. the Compiler JAR is present in the classpath;
   *   3. the extra JARs present in the Scala instance are added to the classpath.
   *   4. the boot classpath is automatically configured; and,
   *   5. the Scala standard library JAR is fetched from the classpath.
   */
  def repl = auto
}

/**
 * Define [[ClasspathOptionsUtil]] object for Scala API consumption.
 */
object ClasspathOptionsUtil extends ClasspathOptionsUtil