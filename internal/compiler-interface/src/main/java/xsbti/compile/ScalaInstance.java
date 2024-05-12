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

	/** A class loader providing access to the classes and resources in all the jars of this Scala instance. */
	ClassLoader loader();

	/** A class loader providing access to the classes and resources in the compiler jar of this Scala instance.
	 * In Scala 2, `loaderCompilerOnly` and `loader` are not different.
	 * But in Scala 3, `loader` contains the `scala3doc` jar and all its dependencies, that are not contained in
	 * `loaderCompilerOnly`
	 * */
	ClassLoader loaderCompilerOnly();

	/** A class loader providing access to the classes and resources in the library jars of this Scala instance. */
	ClassLoader loaderLibraryOnly();

	/** Classpath entries that stores the Scala library classes. */
	File[] libraryJars();

	/** @deprecated Use `libraryJars` instead (since 1.3.0). */
	@Deprecated
	default File libraryJar() {
		return libraryJars()[0];
	}

	/** Classpath entry that stores the Scala compiler classes. */
	File[] compilerJars();

	/** @deprecated Use `compilerJars` instead (since 1.5.0). */
	@Deprecated
	default File compilerJar() { return compilerJars()[0]; }

	/** All the jars except `libraryJars` and `compilerJar`. */
	File[] otherJars();

	/** Classpath entries for the `loader`. */
	File[] allJars();

	/**
	 * The unique identifier for this Scala instance, usually obtained
	 * (but not necessarily) from `compiler.properties` files.
	 */
	String actualVersion();
}
