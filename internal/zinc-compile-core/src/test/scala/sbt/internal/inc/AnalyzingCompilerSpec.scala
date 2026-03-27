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

package sbt.internal.inc

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AnalyzingCompilerSpec extends AnyFlatSpec with Matchers {
  "filterPluginOptions" should "remove -Xplugin: options" in {
    val options = Seq("-deprecation", "-Xplugin:/path/to/semanticdb.jar", "-feature")
    AnalyzingCompiler.filterPluginOptions(options) shouldBe Seq("-deprecation", "-feature")
  }

  it should "remove -Xplugin-require: options" in {
    val options = Seq("-deprecation", "-Xplugin-require:semanticdb", "-feature")
    AnalyzingCompiler.filterPluginOptions(options) shouldBe Seq("-deprecation", "-feature")
  }

  it should "remove -P: plugin options" in {
    val options = Seq("-deprecation", "-P:semanticdb:targetroot:/tmp/target", "-feature")
    AnalyzingCompiler.filterPluginOptions(options) shouldBe Seq("-deprecation", "-feature")
  }

  it should "remove all plugin-related options at once" in {
    val options = Seq(
      "-deprecation",
      "-Xplugin:/path/to/semanticdb.jar",
      "-Xplugin-require:semanticdb",
      "-P:semanticdb:targetroot:/tmp/target",
      "-feature",
      "-Xplugin:/path/to/other-plugin.jar",
      "-P:other-plugin:setting:value"
    )
    AnalyzingCompiler.filterPluginOptions(options) shouldBe Seq("-deprecation", "-feature")
  }

  it should "keep all options when no plugin options are present" in {
    val options = Seq("-deprecation", "-feature", "-unchecked", "-Xlint")
    AnalyzingCompiler.filterPluginOptions(options) shouldBe options
  }

  it should "handle empty options" in {
    AnalyzingCompiler.filterPluginOptions(Seq.empty) shouldBe Seq.empty
  }
}
