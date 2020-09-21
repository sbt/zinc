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

package xsbti.api

import java.util.function.Supplier

/**
 * Proxy `SafeLazy` functionality from the Java implementation
 * implementation in xsbt.api.SafeLazy to Scala helpers.
 *
 * The implementation of these helpers are not reused between each
 * other because they create intermediate anonymous functions and
 * the price of a new object in this hot path is not worth it.
 */
object SafeLazyProxy {

  /** Return a lazy implementation of a Scala by-name parameter. */
  def apply[T](s: => T): Lazy[T] = {
    val sbtThunk = new Supplier[T] { override def get() = s }
    SafeLazy.apply(sbtThunk)
  }

  /** Return a lazy implementation of a strict value. */
  def strict[T](s: T): Lazy[T] = {
    val sbtThunk = new Supplier[T] { override def get() = s }
    SafeLazy.apply(sbtThunk)
  }

}
