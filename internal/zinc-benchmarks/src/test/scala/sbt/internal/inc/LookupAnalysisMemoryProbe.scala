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

import java.lang.management.ManagementFactory
import java.net.URLClassLoader
import java.nio.file.{ Files, Paths }

/** External JOL is measurement tooling, not a Zinc dependency. Requires self-attach permission. */
object LookupAnalysisMemoryProbe {
  def main(args: Array[String]): Unit = {
    require(args.length % 2 == 0, "Expected --jol-jar, --scenario and --output pairs")
    val options = args.grouped(2).map(pair => pair(0) -> pair(1)).toMap
    val jar = Paths.get(options("--jol-jar"))
    val scenarioName = options("--scenario")
    val scenario = LookupAnalysisFixture.scenarios(scenarioName)
    val loader = new URLClassLoader(Array(jar.toUri.toURL), getClass.getClassLoader)
    try {
      val vmClass = loader.loadClass("org.openjdk.jol.vm.VM")
      val vm = vmClass.getMethod("current").invoke(null)
      val vmInterface = loader.loadClass("org.openjdk.jol.vm.VirtualMachine")
      val details = vmInterface.getMethod("details").invoke(vm).toString
      val instrumentationClass = loader.loadClass("org.openjdk.jol.vm.InstrumentationSupport")
      val instrumentation = instrumentationClass.getDeclaredField("INSTRUMENTATION")
      instrumentation.setAccessible(true)
      require(
        instrumentation.get(null) != null,
        "JOL needs Instrumentation for measured sizes; use -Djdk.attach.allowAttachSelf=true"
      )
      val graphClass = loader.loadClass("org.openjdk.jol.info.GraphLayout")
      val parse = graphClass.getMethod("parseInstance", classOf[Array[Object]])
      val totalSize = graphClass.getMethod("totalSize")
      val fixture = scenario.fixture()
      val lookup = fixture.newLookup()
      // Keep the same shared fixture graph live at every measurement. Only the derived index is new.
      require(lookup.analyses.size == scenario.analysisCount)
      def footprint(): Long = {
        val roots: Array[Object] = Array(fixture, lookup)
        val graph = parse.invoke(null, roots.asInstanceOf[Object])
        totalSize.invoke(graph).asInstanceOf[java.lang.Long].longValue()
      }
      val before = footprint()
      lookup.lookupAnalysis("absent.Initial")
      val initialized = footprint()
      for (i <- 0 until 10000) lookup.lookupAnalysis(s"absent.First$i")
      val afterTenThousand = footprint()
      for (i <- 0 until 100000) lookup.lookupAnalysis(s"absent.Second$i")
      val afterHundredThousand = footprint()
      val uniqueNames =
        fixture.analyses.iterator.flatMap(_.relations.productClassName._2s).toSet.size
      def quote(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
          .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
      val fields = Vector(
        "scenario" -> quote(scenarioName),
        "jolJar" -> quote(jar.toString),
        "vm" -> quote(details),
        "jvmArguments" -> quote(ManagementFactory.getRuntimeMXBean.getInputArguments.toString),
        "measuredWithInstrumentation" -> "true",
        "uniqueNames" -> uniqueNames.toString,
        "beforeBytes" -> before.toString,
        "initializedBytes" -> initialized.toString,
        "indexDeltaBytes" -> (initialized - before).toString,
        "after10000MissesBytes" -> afterTenThousand.toString,
        "after110000MissesBytes" -> afterHundredThousand.toString
      )
      val json = fields.map { case (key, value) => s"  ${quote(key)}: $value" }
        .mkString("{\n", ",\n", "\n}\n")
      Files.writeString(Paths.get(options("--output")), json)
      println(json)
    } finally loader.close()
  }
}
