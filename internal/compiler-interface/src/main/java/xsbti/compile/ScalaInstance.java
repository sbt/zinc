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

import java.io.File;

/**
 * A Scala instance encapsulates all the information that is bound to a concrete
 * Scala version, like the ClassLoader or all the JARs required
 * for Scala compilation: library jar, compiler jar and others.
 *
 * Both a `ClassLoader` and the jars are required because the compiler's
 * boot classpath requires the location of the library and compiler jar
 * on the classpath to compile any Scala program and macros.
 *
 * <b>NOTE</b>: A "jar" can actually be any valid classpath entry.
 */
public interface ScalaInstance {
	/**
	 * Scala version for this {@link ScalaInstance}.
	 *
	 * It need not to be unique and can be dynamic (e.g. 2.10.0-SNAPSHOT).
	 */
	String version();

	/** A class loader providing access to the classes and resources in the library and compiler jars. */
	ClassLoader loader();

	/** A class loader providing access to the classes and resources in the library. */
	ClassLoader loaderLibraryOnly();

	/** Only `jars` can be reliably provided for modularized Scala. */
	File libraryJar();

	/** Only `jars` can be reliably provided for modularized Scala. */
	File compilerJar();

	/** @deprecated Only `jars` can be reliably provided for modularized Scala (since 0.13.0). */
	@Deprecated
	File[] otherJars();

	/** All jar files provided by this Scala instance. */
	File[] allJars();

	/**
	 * The unique identifier for this Scala instance, usually obtained
	 * (but not necessarily) from `compiler.properties` files.
	 */
	String actualVersion();
}
