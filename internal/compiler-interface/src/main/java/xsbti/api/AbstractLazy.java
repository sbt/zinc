/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.api;

import java.io.ObjectStreamException;

public abstract class AbstractLazy<T> implements Lazy<T>, java.io.Serializable
{
	// `writeReplace` must be `protected`, so that the `Impl` subclass
	// inherits the serialization override.
	//
	// (See source-dependencies/java-analysis-serialization-error, which would
	//  crash trying to serialize an AbstractLazy, because it pulled in an
	//  unserializable type eventually.)
	protected Object writeReplace() throws ObjectStreamException
	{
		return new StrictLazy<T>(get());
	}
	private static final class StrictLazy<T> implements Lazy<T>, java.io.Serializable
	{
		private final T value;
		StrictLazy(T t)
		{
			value = t;
		}
		public T get()
		{
			return value;
		}
	}
}