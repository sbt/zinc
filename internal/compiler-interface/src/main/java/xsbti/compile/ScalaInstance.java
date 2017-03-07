/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

/**
 * A Scala instance encapsulates all the information that is bound to a concrete
 * Scala version, like the {@link ClassLoader loader} or all the JARs required
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

	/** @deprecated Only `jars` can be reliably provided for modularized Scala (since 0.13.0). */
	@Deprecated
	File libraryJar();

	/** @deprecated Only `jars` can be reliably provided for modularized Scala (since 0.13.0). */
	@Deprecated
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
