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

import java.util.function.Supplier;

/**
 * Implement a Scala `lazy val` in Java for the facing sbt interface.
 *
 * It holds a reference to a thunk that is lazily evaluated and then
 * its reference is clear to avoid memory leaks in memory-intensive code.
 * It needs to be defined in [[xsbti]] or a subpackage, see [[xsbti.api.Lazy]]
 * for similar definitions.
 */
public final class SafeLazy {

  /* We do not use conversions from and to Scala functions because [[xsbti]]
   * cannot hold any reference to Scala code nor the Scala library. */

  /** Return a sbt [[xsbti.api.Lazy]] from a given Scala parameterless function. */
  public static <T> xsbti.api.Lazy<T> apply(Supplier<T> sbtThunk) {
    return new Impl<T>(sbtThunk);
  }

  /** Return a sbt [[xsbti.api.Lazy]] from a strict value. */
  public static <T> xsbti.api.Lazy<T> strict(T value) {
    // Convert strict parameter to sbt function returning it
    return apply(new Supplier<T>() {
      @Override
      public T get() {
        return value;
      }
    });
  }

  private static final class Impl<T> extends xsbti.api.AbstractLazy<T> {
    private Supplier<T> thunk = null;
    private T result = null;

    Impl(Supplier<T> thunk) {
      this.thunk = thunk;
    }

    /**
     * Return cached result or force lazy evaluation.
     *
     * Don't call it in a multi-threaded environment.
     */
    public synchronized T get() {
      if (thunk == null) return result;
      else {
        result = thunk.get();

        thunk = null; // also allows it to be GC'ed

        return result;
      }
    }
  }
}


