/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * Define the order in which Scala and Java compilation should happen.
 *
 * This compilation order matters when the sources to be compiled are both
 * Scala and Java sources, in which case the Zinc compiler needs a strategy
 * to deal with the compilation order. Therefore, this setting has no effect
 * if only Java or Scala sources are being compiled.
 */
public enum CompileOrder {
	/**
	 * Allow Scala sources to depend on Java sources and allow Java sources to
	 * depend on Scala sources.
     *
	 * Under this mode, both Java and Scala sources can depend on each other.
	 * Java sources are also passed to the Scala compiler, which parses them,
	 * populates the symbol table and lifts them to Scala trees without
	 * generating class files for the Java trees.
	 *
	 * Then, the incremental compiler will add the generated Scala class files
	 * to the classpath of the Java compiler so that Java sources can depend
	 * on Scala sources.
     *
	 * For more information on the way Mixed and ScalaThenJava mode behave,
	 * see <a href="https://github.com/sbt/zinc/issues/235">this link</a>.
	 */
	Mixed,

	/**
	 * Allow Scala sources to depend on the Java sources, but it does not allow
	 * Java sources to depend on Scala sources.
     *
	 * When mixed compilation is not required, it's generally more efficient
	 * {@link CompileOrder#JavaThenScala} than {@link CompileOrder#ScalaThenJava}
	 * because the Scala compiler will not parse Java sources, it will just
	 * unpickle the symbol information from class files.
	 */
	JavaThenScala,

	/**
     * Allow Java sources to depend on Scala sources, but it does not allow Java
	 * sources to depend on Scala sources.
     *
     * Because of the way the Scala compiler works with regard to Java sources,
	 * Zinc has to enforce this mode by removing Java sources from the actual
	 * source arguments that are passed to the Scala compiler, otherwise it'll
	 * behave exactly as {@link CompileOrder#Mixed}.
	 *
	 * For more information on the way Mixed and ScalaThenJava mode behave,
	 * see <a href="https://github.com/sbt/zinc/issues/235">this link</a>.
	 */
	ScalaThenJava
}