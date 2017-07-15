/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
    private Supplier<T> thunk;
    private T result;
    private boolean flag = false;

    Impl(Supplier<T> thunk) {
      this.thunk = thunk;
    }

    /**
     * Return cached result or force lazy evaluation.
     * 
     * Don't call it in a multi-threaded environment.
     */
    public T get() {
      if (flag) return result;
      else {
        result = thunk.get();
        flag = true;
        // Clear reference so that thunk is GC'ed
        thunk = null;
        return result;
      }
    }
  }
}


