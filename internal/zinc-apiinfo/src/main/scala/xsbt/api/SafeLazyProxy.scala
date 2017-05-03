/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.api

/**
 * Proxy `SafeLazy` functionality from the Java implementation
 * implementation in [[xsbt.api.SafeLazy]] to Scala helpers.
 *
 * The implementation of these helpers are not reused between each
 * other because they create intermediate anonymous functions and
 * the price of a new object in this hot path is not worth it.
 */
object SafeLazyProxy {

  /**
   * Return a lazy implementation of a Scala by-name parameter.
   */
  def apply[T](s: => T): Lazy[T] = {
    val sbtThunk = new xsbti.F0[T] { def apply() = s }
    SafeLazy.apply(sbtThunk)
  }

  /**
   * Return a lazy implementation of a strict value.
   */
  def strict[T](s: T): Lazy[T] = {
    val sbtThunk = new xsbti.F0[T] { def apply() = s }
    SafeLazy.apply(sbtThunk)
  }
}
