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

package xsbti.api;

import java.util.function.Supplier;

/**
 * Implement a Scala `lazy val` in Java for the facing sbt interface.
 * <p>
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
    return new StrictImpl<T>(value);
  }

  private static final class Thunky<T> {
    final Supplier<T> thunk;
    final T result;
    Thunky(Supplier<T> thunk, T result) {
      this.thunk = thunk;
      this.result = result;
    }
  }

  private static final class Impl<T> extends xsbti.api.AbstractLazy<T> {
    private Thunky<T> thunky;

    Impl(Supplier<T> thunk) {
      this.thunky = new Thunky<>(thunk, null);
    }

    public T get() {
      Thunky<T> t = thunky;
      if (t.result == null) {
        T r = t.thunk.get();
        t = new Thunky<>(null, r);
        thunky = t;
      }
      return t.result;
    }
  }

  private static final class StrictImpl<T> extends xsbti.api.AbstractLazy<T> {
    private final T value;
    StrictImpl(T value) { this.value = value; }
    public T get() { return value; }
  }
}
