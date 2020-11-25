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

package sbt
package internal
package inc
package classpath

import java.net.{ URL, URLClassLoader }
import java.util

/**
 * A [[DualLoader]] is an `URLClassLoader`` that also contains a dual `ClassLoader`
 * The `dual` loader preempts `super` on some class or resource lookups.
 *
 * If `isDualClass` returns `true` for a class name, class lookup delegates to `dual`.
 * Otherwise class lookup is performed by `super`.
 *
 * If `isDualResource` is `true` for a resource path, lookup delegates to `dual` only.
 * Otherwise resource lookup is performed by `super`.
 */
class DualLoader(
    urls: Array[URL],
    dual: ClassLoader,
    isDualClass: String => Boolean,
    isDualResource: String => Boolean
) extends URLClassLoader(urls, null) {
  override def loadClass(name: String, resolve: Boolean): Class[_] = {
    if (isDualClass(name)) {
      val c = dual.loadClass(name)
      if (resolve) resolveClass(c)
      c
    } else super.loadClass(name, resolve)
  }

  override def getResource(name: String): URL = {
    if (isDualResource(name)) dual.getResource(name)
    else super.getResource(name)
  }

  override def getResources(name: String): util.Enumeration[URL] = {
    if (isDualResource(name)) dual.getResources(name)
    else super.getResources(name)
  }

  override def toString = s"DualLoader(urls = $urls, dual = $dual)"
}
