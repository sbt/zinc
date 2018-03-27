package sbt.inc.text

import org.scalacheck.Prop._
import org.scalacheck._
import sbt.internal.inc.text._

object Base64Specification extends Properties("Base64") {

  val java678Encoder = new Java678Encoder
  val java89Encoder = new Java89Encoder

  property("java678") = forAll { (bs: Array[Byte]) =>
    java678Encoder.decode(java678Encoder.encode(bs)).deep == bs.deep
  }

  property("java89") = forAll { (bs: Array[Byte]) =>
    java89Encoder.decode(java89Encoder.encode(bs)).deep == bs.deep
  }
}
