package sbt.inc

import java.io.File

object TestResource {
  def knownSampleGoodFile0 =
    new File(classOf[TestResource].getResource("Good.scala").toURI)
  def fooSampleFile0 =
    new File(classOf[TestResource].getResource("Foo.scala").toURI)
  def binarySampleFile0 =
    new File(classOf[TestResource].getResource("sample-binary_2.11-0.1.jar").toURI)
  def dependerFile0 =
    new File(classOf[TestResource].getResource("Depender.scala").toURI)
  def ext1File0 =
    new File(classOf[TestResource].getResource("Ext1.scala").toURI)
}
class TestResource
