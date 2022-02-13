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

object ModuleUtilities {

  /**
   * Reflectively loads and returns the companion object for top-level class `className` from `loader`.
   * The class name should not include the `$` that scalac appends to the underlying jvm class for
   * a companion object.
   */
  def getObject(className: String, loader: ClassLoader): AnyRef = {
    val obj = Class.forName(className + "$", true, loader)
    val singletonField = obj.getField("MODULE$")
    singletonField.get(null)
  }

  def getCheckedObject[T](className: String, loader: ClassLoader)(implicit
      mf: reflect.ClassTag[T]
  ): T =
    mf.runtimeClass.cast(getObject(className, loader)).asInstanceOf[T]

  def getCheckedObjects[T](classNames: Seq[String], loader: ClassLoader)(implicit
      mf: reflect.ClassTag[T]
  ): Seq[(String, T)] =
    classNames.map(name => (name, getCheckedObject(name, loader)))
}
