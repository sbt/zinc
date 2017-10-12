/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc

import java.io.File

object TestResource {
  def knownSampleGoodFile0 =
    new File(getClass.getClassLoader.getResource("sources/Good.scala").toURI)
  def fooSampleFile0 =
    new File(classOf[TestResource].getResource("Foo.scala").toURI)
  def binarySampleFile0 =
    new File(classOf[TestResource].getResource("sample-binary_2.11-0.1.jar").toURI)
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
