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

/** A `LookupImpl` over in-memory analyses, without a compiler or files on disk. */
private[inc] object LookupAnalysisFixture:
  /** An analysis whose classes have these binary names. */
  def analysis(binaryNames: Seq[String]): Analysis =
    val classes = binaryNames.map(name => s"source.$name" -> name)
    Analysis.empty.copy(relations = Relations.empty.addClasses(VirtualFileRef.of("src"), classes))

  /** Counts the calls to `relations`, which is how a lookup visits an analysis. */
  def counting(analysis: Analysis, visits: AtomicLong): Analysis =
    proxy(classOf[Analysis]) { (m, args) =>
      if m.getName == "relations" then visits.incrementAndGet()
      m.invoke(analysis, args*)
    }

  private def proxy[A](cls: Class[A])(f: (Method, Array[Object]) => Object): A =
    val handler: InvocationHandler =
      (_, m, args) => f(m, if args == null then Array.empty[Object] else args)
    cls.cast(Proxy.newProxyInstance(cls.getClassLoader, Array(cls), handler))

  private val instance = proxy(classOf[ScalaInstance]) { (m, _) =>
    m.getName match
      case "allJars" | "libraryJars" | "compilerJars" | "otherJars" => Array.empty[java.io.File]
      case "version" | "actualVersion"                              => "3.9.0"
      case name => throw new UnsupportedOperationException(s"Fixture ScalaInstance.$name")
  }

  private val compiler = proxy(classOf[ScalaCompiler]) { (m, _) =>
    m.getName match
      case "scalaInstance"    => instance
      case "classpathOptions" => ClasspathOptions.of(false, false, false, false, false)
      case name => throw new UnsupportedOperationException(s"Fixture ScalaCompiler.$name")
  }

  /** One classpath entry per element of `entries`; `None` is an entry without an analysis. */
  final class Fixture(entries: Vector[Option[Analysis]]):
    private val files: Vector[VirtualFile] =
      Vector.tabulate(entries.size)(i => PlainVirtualFile(Paths.get(s"/lookup-fixture/cp-$i")))
    private val byFile = files.zip(entries).toMap
    private val perEntry = new PerClasspathEntryLookup:
      def analysis(file: VirtualFile): Optional[CompileAnalysis] =
        Optional.ofNullable(byFile(file).orNull)
      def definesClass(file: VirtualFile): DefinesClass = _ => false
    private val config = new CompileConfiguration(
      sources = Nil,
      converter = PlainVirtualFileConverter.converter,
      classpath = files,
      previousAnalysis = Analysis.empty,
      previousSetup = None,
      currentSetup = MiniSetup.of(
        null,
        MiniOptions.of(Array.empty, Array.empty, Array.empty),
        "3.9.0",
        CompileOrder.Mixed,
        true,
        Array.empty
      ),
      progress = None,
      perClasspathEntryLookup = perEntry,
      reporter = null,
      compiler = compiler,
      javac = null,
      cache = null,
      incOptions = IncOptions.of(),
      outputJarContent = null,
      earlyOutput = None,
      earlyAnalysisStore = None,
      stampReader = null,
    )

    def newLookup(): LookupImpl = new LookupImpl(config, None)
  end Fixture
end LookupAnalysisFixture
