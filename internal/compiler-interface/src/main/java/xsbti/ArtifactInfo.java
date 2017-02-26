/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

/**
 * Define constants of Scala compiler useful for artifact resolution.
 */
public final class ArtifactInfo {
	/** Define the name of the Scala organization. */
	public static final String ScalaOrganization = "org.scala-lang";

	/** Define the name used to identify the sbt organization. */
	public static final String SbtOrganization = "org.scala-sbt";

	/** Define the ID used to identify the Scala library. */
	public static final String ScalaLibraryID = "scala-library";

	/** Define the ID used to identify the Scala compiler. */
	public static final String ScalaCompilerID = "scala-compiler";
}