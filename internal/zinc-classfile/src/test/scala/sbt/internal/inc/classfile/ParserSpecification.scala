package sbt
package internal
package inc
package classfile

import org.scalacheck._
import Prop._

object ParserSpecification extends Properties("Parser") {
  property("able to parse all relevant classes") =
    Prop.forAll(classes) { (c: Class[_]) =>
      Parser(sbt.io.IO.classfileLocation(c)) ne null
    }

  implicit def classes: Gen[Class[_]] =
    Gen.oneOf(
      this.getClass,
      classOf[java.lang.Integer],
      classOf[java.util.AbstractMap.SimpleEntry[String, String]],
      classOf[String],
      classOf[Thread],
      classOf[Properties]
    )
}
