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

package sbt.inc

import java.io.File

object TestResource {
  def knownSampleGoodFile0 =
    new File(getClass.getClassLoader.getResource("sources/Good.scala").toURI)
  def fooSampleFile0 =
    new File(classOf[TestResource].getResource("Foo.scala").toURI)
  def binarySampleFile0 =
    new File(classOf[TestResource].getResource("sample-binary_2.12-0.1.jar").toURI)
  def dependerFile0 =
    new File(classOf[TestResource].getResource("Depender.scala").toURI)
  def depender2File0 =
    new File(classOf[TestResource].getResource("Depender2.scala").toURI)
  def ext1File0 =
    new File(classOf[TestResource].getResource("Ext1.scala").toURI)
  def ext2File0 =
    new File(classOf[TestResource].getResource("Ext2.scala").toURI)
}
class TestResource
