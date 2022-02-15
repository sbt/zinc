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
package classfile

import sbt.internal.util.ConsoleLogger

import org.scalacheck._
import Prop._

object ParserSpecification extends Properties("Parser") {

  val log = ConsoleLogger()

  property("able to parse all relevant classes") = Prop.forAll(classes) { (c: Class[_]) =>
    Parser(sbt.io.IO.classfileLocation(c), log) ne null
  }

  implicit def classes: Gen[Class[_]] =
    Gen.oneOf(
      this.getClass,
      classOf[java.lang.Integer],
      classOf[java.util.AbstractMap.SimpleEntry[String, String]],
      classOf[String],
      classOf[Thread],
      classOf[Properties],
      classOf[java.lang.annotation.Retention]
    )
}
