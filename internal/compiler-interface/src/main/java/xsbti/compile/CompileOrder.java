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

/**
 * Defines the order in which Scala and Java compilation are called within
 * a single cycle of incremental compilation.
 *
 * The default is "mixed" order. See below for possible reasons to change
 * the default.
 *
 * In general, because incremental compilation happens over the course
 * of time as a series of direct and indirect invalidation and compilation,
 * the ordering here is more of a implementation detail rather than an
 * invariant you can rely on to enforce visibility etc.
 */
public enum CompileOrder {
  /**
   * Scalac is called on all sources in both languages, then Javac is called
   * on the Java sources only. This is the default behavior.
   *
   * In this mode, Java and Scala sources can have cyclic dependencies
   * upon each other.
   *
   * When Java source are passed to the Scala compiler, it parses them,
   * populates the symbol table and lifts them to Scala trees without
   * generating class files for the Java trees.
   * Then the incremental compiler will add the generated Scala class files
   * to the classpath of the Java compiler so that Java sources can depend
   * on Scala sources.
   */
  Mixed,

  /**
   * Javac is called then Scalac is called.
   *
   * Because Javac is called first, during the first round of compilation
   * it does not allow Java sources to depend on Scala sources.
   * Scala sources may still depend on Java sources.
   *
   * We know of three reasons you might choose this option:
   *
   * 1) If mixed compilation isn't required, this option has the potential to
   * be faster, since Scalac doesn't need to spend time parsing Java sources.
   * (Instead, it will just unpickle the symbol information from class files.)
   *
   * 2) Your Java sources use newer Java language features which
   * Scalac isn't yet able to understand.
   *
   * 3) You wish to enforce, as an architectural constraint, that
   * your Java sources never depend on your Scala sources.
   */
  JavaThenScala,

  /**
   * Scalac is called on only Scala sources only, then Javac is called
   * on Java sources only.
   *
   * Deprecated because it was never properly implemented and only marginal
   * use cases are known to exist.
   *
   * @deprecated Use {@link CompileOrder#Mixed} instead, or move the Java
   * code to its own subproject.
   */
  @Deprecated
  ScalaThenJava
}
