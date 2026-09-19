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

package sbt
package internal
package inc
package classpath

import java.net.URL
import java.util.Enumeration
import java.util.Collections

/** A class loader that always fails to load classes and resources. */
final class NullLoader extends ClassLoader:
  override final def loadClass(className: String, resolve: Boolean): Class[?] =
    throw new ClassNotFoundException("No classes can be loaded from the null loader")
  override def getResource(name: String): URL = null
  override def getResources(name: String): Enumeration[URL] =
    Collections.enumeration(Collections.emptyList())
  override def toString = "NullLoader"

/** Exception thrown when `loaderA` and `loaderB` load a different Class for the same name. */
class DifferentLoaders(message: String, val loaderA: ClassLoader, val loaderB: ClassLoader)
    extends ClassNotFoundException(message)

/**
 * A ClassLoader with two parents `parentA` and `parentB`.  The predicates direct lookups towards one parent or the other.
 *
 * If `aOnlyClasses` returns `true` for a class name, class lookup delegates to `parentA` only.
 * Otherwise, if `bOnlyClasses` returns `true` for a class name, class lookup delegates to `parentB` only.
 * If both `aOnlyClasses` and `bOnlyClasses` are `false` for a given class name, both class loaders must load the same Class or
 * a [[DifferentLoaders]] exception is thrown.
 *
 * If `aOnlyResources` is `true` for a resource path, lookup delegates to `parentA` only.
 * Otherwise, if `bOnlyResources` is `true` for a resource path, lookup delegates to `parentB` only.
 * If neither are `true` for a resource path and either `parentA` or `parentB` return a valid URL, that valid URL is returned.
 */
class DualLoader(
    parentA: ClassLoader,
    aOnlyClasses: String => Boolean,
    aOnlyResources: String => Boolean,
    parentB: ClassLoader,
    bOnlyClasses: String => Boolean,
    bOnlyResources: String => Boolean
) extends ClassLoader(new NullLoader):
  def this(
      parentA: ClassLoader,
      aOnly: String => Boolean,
      parentB: ClassLoader,
      bOnly: String => Boolean
  ) =
    this(parentA, aOnly, aOnly, parentB, bOnly, bOnly)
  override final def loadClass(className: String, resolve: Boolean): Class[?] =
    val c =
      if aOnlyClasses(className) then
        parentA.loadClass(className)
      else if bOnlyClasses(className) then
        parentB.loadClass(className)
      else
        val classA = parentA.loadClass(className)
        val classB = parentB.loadClass(className)
        if classA.getClassLoader eq classB.getClassLoader then
          classA
        else
          throw new DifferentLoaders(
            "Parent class loaders returned different classes for '" + className + "'",
            classA.getClassLoader,
            classB.getClassLoader
          )
    if resolve then
      resolveClass(c)
    c
  override def getResource(name: String): URL =
    if aOnlyResources(name) then
      parentA.getResource(name)
    else if bOnlyResources(name) then
      parentB.getResource(name)
    else
      val urlA = parentA.getResource(name)
      val urlB = parentB.getResource(name)
      if urlA eq null then
        urlB
      else
        urlA
  override def getResources(name: String): Enumeration[URL] =
    if aOnlyResources(name) then
      parentA.getResources(name)
    else if bOnlyResources(name) then
      parentB.getResources(name)
    else
      val urlsA = parentA.getResources(name)
      val urlsB = parentB.getResources(name)
      if !urlsA.hasMoreElements then
        urlsB
      else if !urlsB.hasMoreElements then
        urlsA
      else
        new DualEnumeration(urlsA, urlsB)

  override def toString = s"DualLoader(a = $parentA, b = $parentB)"
end DualLoader

/** Concatenates `a` and `b` into a single `Enumeration`.*/
final class DualEnumeration[T](a: Enumeration[T], b: Enumeration[T]) extends Enumeration[T]:
  // invariant: current.hasMoreElements or current eq b
  private var current = if a.hasMoreElements then a else b
  def hasMoreElements = current.hasMoreElements
  def nextElement =
    val element = current.nextElement
    if !current.hasMoreElements then
      current = b
    element
