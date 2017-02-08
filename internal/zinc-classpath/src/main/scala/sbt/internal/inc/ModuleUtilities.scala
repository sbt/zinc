/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
  def getObject(className: String, loader: ClassLoader): AnyRef =
    {
      val obj = Class.forName(className + "$", true, loader)
      val singletonField = obj.getField("MODULE$")
      singletonField.get(null)
    }

  def getCheckedObject[T](className: String, loader: ClassLoader)(implicit mf: reflect.ClassTag[T]): T =
    mf.runtimeClass.cast(getObject(className, loader)).asInstanceOf[T]

  def getCheckedObjects[T](classNames: Seq[String], loader: ClassLoader)(implicit mf: reflect.ClassTag[T]): Seq[(String, T)] =
    classNames.map(name => (name, getCheckedObject(name, loader)))
}
