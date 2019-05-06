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