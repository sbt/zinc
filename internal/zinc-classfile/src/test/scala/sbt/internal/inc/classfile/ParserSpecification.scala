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

package sbt
package internal
package inc
package classfile

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.util.ConsoleLogger

object ParserSpecification extends Properties:
  override def tests: List[Test] =
    sampleClasses.map(c =>
      example(s"classfile.Parser should not crash when parsing $c", parses(c))
    ) ++
      List(
        property("not crash when parsing any sample class", propParses),
        example(
          "parse InnerClasses attribute for AbstractMap.SimpleEntry",
          simpleEntryInnerClasses
        ),
        example("parse InnerClasses attribute for AbstractMap", abstractMapInnerClasses),
      )

  private val sampleClasses = List[Class[?]](
    this.getClass,
    classOf[java.lang.Integer],
    classOf[java.util.AbstractMap.SimpleEntry[String, String]],
    classOf[String],
    classOf[Thread],
    classOf[org.scalacheck.Properties],
    classOf[hedgehog.runner.Properties],
    // exercises meta-annotation parsing
    classOf[java.lang.annotation.Retention]
    // I thought it would be nice to throw in a nested annotation example here,
    // but I couldn't find one that we could use without having to add another
    // JAR to the test classpath. it's fine, we have nested annotation testing
    // over in AnalyzeSpecification
  )

  private def parse(c: Class[?]): ClassFile =
    val logger = ConsoleLogger()
    // logger.setLevel(sbt.util.Level.Debug)
    Parser(sbt.io.IO.classfileLocation(c), logger)

  private def parses(c: Class[?]): Result =
    val classfile = parse(c)
    Result.all(List(Result.assert(classfile ne null), Result.assert(classfile.types.nonEmpty)))

  private val genClass: Gen[Class[?]] =
    Gen.element1[Class[?]](
      classOf[java.util.HashMap[?, ?]],
      classOf[java.util.concurrent.ConcurrentHashMap[?, ?]],
      classOf[java.util.Optional[?]],
      classOf[java.lang.Enum[?]],
      classOf[java.io.BufferedReader],
      classOf[java.nio.file.Files],
      classOf[scala.collection.immutable.List[?]],
      classOf[scala.Option[?]],
      classOf[scala.util.Try[?]],
      classOf[hedgehog.core.GenT[?]],
      classOf[Parser.type],
    )

  def propParses: Property =
    for c <- genClass.forAll
    yield parses(c).log(s"class: $c")

  private def simpleEntryInnerClasses: Result =
    val cf = parse(classOf[java.util.AbstractMap.SimpleEntry[String, String]])
    val self = cf.innerClasses.find(_.innerClassName == "java.util.AbstractMap$SimpleEntry")
    Result.all(
      List(
        Result.assert(cf.innerClasses.nonEmpty),
        self.map(_.outerClassName) ==== Some("java.util.AbstractMap"),
      )
    )

  private def abstractMapInnerClasses: Result =
    val cf = parse(classOf[java.util.AbstractMap[?, ?]])
    val entry = cf.innerClasses.find(_.innerClassName == "java.util.AbstractMap$SimpleEntry")
    Result.all(
      List(
        entry.map(_.outerClassName) ==== Some("java.util.AbstractMap"),
        entry.map(_.isPublic) ==== Some(true),
      )
    )
end ParserSpecification
