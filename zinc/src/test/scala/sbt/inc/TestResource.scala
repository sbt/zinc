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

package sbt.inc

import java.nio.file.Paths

object TestResource {
  def knownSampleGoodFile0 =
    Paths.get(getClass.getClassLoader.getResource("sources/Good.scala").toURI)
  def fooSampleFile0 =
    Paths.get(classOf[TestResource].getResource("Foo.scala").toURI)
  def binarySampleFile0 =
    Paths.get(classOf[TestResource].getResource("sample-binary_2.12-0.1.jar").toURI)
  def dependerFile0 =
    Paths.get(classOf[TestResource].getResource("Depender.scala").toURI)
  def ext1File0 =
    Paths.get(classOf[TestResource].getResource("Ext1.scala").toURI)
  def ext2File0 =
    Paths.get(classOf[TestResource].getResource("Ext2.scala").toURI)
}
class TestResource
