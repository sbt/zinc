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

import java.lang.reflect.{ InvocationHandler, Method, Proxy }
import java.nio.file.Paths
import java.util.Optional
import java.util.concurrent.atomic.AtomicLong
import xsbti.{ VirtualFile, VirtualFileRef }
import xsbti.compile.*

private[inc] object LookupAnalysisFixture {
  final case class Scenario(classpathSize: Int, analysisCount: Int, classesPerAnalysis: Int) {
    def binaryName(analysisIndex: Int, classIndex: Int): String =
      s"upstream$analysisIndex.pkg.Class$classIndex"

    def fixture(): Fixture = new Fixture(Vector.tabulate(classpathSize) { i =>
      if (i >= analysisCount) None
      else Some(analysis(
        i,
        Vector.tabulate(classesPerAnalysis) { j =>
          s"upstream$i.pkg.Source$j" -> binaryName(i, j)
        }
      ))
    })

    def queries(mix: String, count: Int): Array[String] = {
      require(count > 0)
      require(Set("first", "last", "miss", "mixed")(mix))
      val random = new scala.util.Random(593L)
      val repeatedMiss = "absent.Repeated"
      Array.tabulate(count) { i =>
        val hit = analysisCount > 0 && (mix != "miss") && (mix != "mixed" || i % 2 == 0)
        if (!hit) {
          if (i % 4 < 2) repeatedMiss else s"absent.Distinct$i"
        } else {
          val a = mix match {
            case "first" => 0
            case "last"  => analysisCount - 1
            case _       => random.nextInt(analysisCount)
          }
          val name = binaryName(a, random.nextInt(classesPerAnalysis))
          if (i % 4 < 2) name else new String(name.toCharArray)
        }
      }
    }
  }

  val scenarios: Map[String, Scenario] = Map(
    "empty" -> Scenario(0, 0, 0),
    "small" -> Scenario(32, 2, 100),
    "library-heavy" -> Scenario(2000, 2, 100),
    "upstream-heavy" -> Scenario(2000, 1000, 100),
    "class-heavy" -> Scenario(500, 200, 1000)
  )

  final class Counters {
    val loads = new AtomicLong
    val visits = new AtomicLong
    val defines = new AtomicLong
  }

  def analysis(id: Int, names: Seq[(String, String)], withApi: Boolean = true): Analysis = {
    val apis = if (withApi) {
      names.foldLeft(APIs.empty) { case (all, (sourceName, _)) =>
        all.markInternalAPI(
          sourceName,
          APIs.emptyAnalyzedClass.withName(sourceName).withApiHash(id).withProvenance(s"cp-$id")
        )
      }
    } else APIs.empty
    Analysis.empty.copy(
      relations = Relations.empty.addClasses(VirtualFileRef.of(s"src-$id"), names),
      apis = apis
    )
  }

  private def proxy[A](cls: Class[A])(f: (Method, Array[Object]) => Object): A =
    cls.cast(Proxy.newProxyInstance(
      cls.getClassLoader,
      Array(cls),
      new InvocationHandler {
        def invoke(p: Object, m: Method, args: Array[Object]): Object =
          f(m, if (args == null) Array.empty[Object] else args)
      }
    ))

  private val instance = proxy(classOf[ScalaInstance]) { (m, _) =>
    m.getName match {
      case "allJars" | "libraryJars" | "compilerJars" | "otherJars" => Array.empty[java.io.File]
      case "version" | "actualVersion"                              => "3.9.0"
      case name => throw new UnsupportedOperationException(s"Fixture ScalaInstance.$name")
    }
  }
  private val compiler = proxy(classOf[ScalaCompiler]) { (m, _) =>
    m.getName match {
      case "scalaInstance"    => instance
      case "classpathOptions" => ClasspathOptions.of(false, false, false, false, false)
      case name => throw new UnsupportedOperationException(s"Fixture ScalaCompiler.$name")
    }
  }

  final class Fixture(
      rawEntries: Vector[Option[Analysis]],
      positions: Vector[Int] = Vector.empty,
      counters: Option[Counters] = None,
      options: IncOptions = IncOptions.of()
  ) {
    private val entries = rawEntries.map(_.map { a =>
      counters match {
        case None => a
        case Some(c) => proxy(classOf[Analysis]) { (m, args) =>
            if (m.getName == "relations") c.visits.incrementAndGet()
            m.invoke(a, args*)
          }
      }
    })
    private val files: Vector[VirtualFile] = entries.indices.map { i =>
      PlainVirtualFile(Paths.get(s"/lookup-fixture/cp-$i"))
    }.toVector
    private val order = if (positions.isEmpty) entries.indices.toVector else positions
    val classpath: Vector[VirtualFile] = order.map(files)
    val analyses: Vector[Analysis] = order.flatMap(entries)
    private val byEntry = files.zip(entries).toMap
    private val perEntry = new PerClasspathEntryLookup {
      def analysis(file: VirtualFile): Optional[CompileAnalysis] = {
        counters.foreach(_.loads.incrementAndGet())
        Optional.ofNullable(byEntry(file).orNull)
      }
      def definesClass(file: VirtualFile): DefinesClass = {
        counters.foreach(_.defines.incrementAndGet())
        (_: String) => false
      }
    }
    private val setup = MiniSetup.of(
      null,
      MiniOptions.of(Array.empty, Array.empty, Array.empty),
      "3.9.0",
      CompileOrder.Mixed,
      true,
      Array.empty
    )
    val config = new CompileConfiguration(
      Nil,
      PlainVirtualFileConverter.converter,
      classpath,
      Analysis.empty,
      None,
      setup,
      None,
      perEntry,
      null,
      compiler,
      null,
      null,
      options,
      null,
      None,
      None,
      null
    )
    def newLookup(): LookupImpl = new LookupImpl(config, None)
  }
}
