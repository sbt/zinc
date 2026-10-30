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

package sbt.inc.consistent

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, File }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Paths

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.{ Analysis, AnalysisFormatFixture, SourceInfos }
import sbt.internal.inc.consistent.*
import sbt.util.InterfaceUtil
import xsbti.{ Severity, VirtualFileRef }
import xsbti.compile.{ FileHash, MiniSetup }
import xsbti.compile.analysis.ReadWriteMappers

object ConsistentPortablePathSuite extends Properties:
  override def tests: List[Test] = List(
    example("problem positions are stored by virtual id", virtualized),
    example("classpath entries are stored with forward slashes", slashedClasspath),
    example("only Windows separators are rewritten", slashedSeparators),
  )

  private val id = "${BASE}/src/A.scala"
  private val absolute = new File(new File("machine").getAbsoluteFile, "A.scala")

  private def analysis =
    val pos = InterfaceUtil.position(
      line0 = Some(1),
      content = "val a: Int = \"\"",
      offset0 = Some(13),
      pointer0 = Some(13),
      pointerSpace0 = Some(" " * 13),
      sourcePath0 = Some(id),
      sourceFile0 = Some(absolute),
      startOffset0 = None,
      endOffset0 = None,
      startLine0 = None,
      startColumn0 = None,
      endLine0 = None,
      endColumn0 = None
    )
    val problem = InterfaceUtil.problem(
      "typer",
      pos,
      "type mismatch",
      Severity.Error,
      Some(s"${absolute.getPath}:1:14: type mismatch"),
      None,
      Nil,
      Nil
    )
    val info = SourceInfos.makeInfo(Seq(problem), Nil, Nil)
    Analysis.empty.copy(infos = SourceInfos.empty.add(VirtualFileRef.of(id), info))
  end analysis

  private val format = new ConsistentAnalysisFormat(ReadWriteMappers.getEmptyMappers, true)

  private def write(analysis: Analysis, setup: MiniSetup): ByteArrayOutputStream =
    val out = new ByteArrayOutputStream
    format.write(SerializerFactory.text.serializerFor(out), analysis, setup)
    out

  private def virtualized: Result =
    val out = write(analysis, AnalysisFormatFixture.setup)
    val text = new String(out.toByteArray, UTF_8)
    val (read, _) = format.read(
      SerializerFactory.text.deserializerFor(new ByteArrayInputStream(out.toByteArray))
    )
    val problem =
      read.readSourceInfos.getAllSourceInfos.values.iterator.next.getReportedProblems.head
    Result.all(
      List(
        Result.assert(!text.contains("machine")).log(text),
        problem.rendered.get ==== s"$id:1:14: type mismatch",
        problem.position.sourceFile.get ==== new File(id),
      )
    )

  private def slashedClasspath: Result =
    val setup = AnalysisFormatFixture.setup
    val jar = FileHash.of(Paths.get("${BASE}", "lib", "x.jar"), 1)
    val out = write(Analysis.empty, setup.withOptions(setup.options.withClasspathHash(Array(jar))))
    val text = new String(out.toByteArray, UTF_8)
    Result.assert(text.contains("${BASE}/lib/x.jar")).log(text)

  private def slashedSeparators: Result =
    Result.all(
      List(
        ConsistentAnalysisFormat.slashed("${BASE}\\lib\\x.jar", '\\') ==== "${BASE}/lib/x.jar",
        ConsistentAnalysisFormat.slashed("a\\b", '/') ==== "a\\b",
      )
    )
end ConsistentPortablePathSuite
