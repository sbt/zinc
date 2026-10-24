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

import java.io.{ ByteArrayOutputStream, File }
import java.nio.charset.StandardCharsets.{ ISO_8859_1, UTF_8 }
import java.security.MessageDigest
import java.util.HexFormat

import scala.util.control.NonFatal

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import sbt.internal.inc.{ Analysis, AnalysisFormatFixture }
import sbt.internal.inc.consistent.*
import sbt.io.IO
import xsbti.compile.analysis.ReadWriteMappers

/**
 * Pins the serialized shape of Analysis to `ConsistentAnalysisFormat.VERSION`. The golden files are
 * named after the version they record, so a format change cannot be accepted without either bumping
 * VERSION or editing files that claim to hold an already shipped format.
 *
 * Both renderings are pinned: the text one because it diffs readably, and the binary one because
 * that is what actually ships, and it has encoding machinery (string interning, length prefixes)
 * that the text serializer does not share.
 */
object ConsistentAnalysisFormatVersionSuite extends Properties:
  override def tests: List[Test] = List(
    example("serialized analysis matches the golden files for its format version", matchesGolden),
    example("serializing twice produces the same bytes", twice),
    example(
      "a Windows rendering of the dummy output path canonicalizes to the golden form",
      windowsRendering
    ),
    property(
      "canonicalizing any Windows rendering of the dummy paths preserves the byte length",
      propCanonicalizeBinaryLength
    ),
  )

  private def matchesGolden: Result =
    if !testData.isDirectory then
      Result.failure.log(
        s"test-data not found at ${testData.getCanonicalPath}; did the forked working directory move?"
      )
    else
      val text = renderText()
      val version = formatVersion(text)
      // A golden for a superseded version could mask a VERSION regression, so only the current
      // version's files may exist.
      val stale = testData
        .listFiles()
        .map(_.getName)
        .filter(_.matches("analysis-format-\\d+\\.(txt|sha256)"))
        .filterNot(n => n == goldenFile(version).getName || n == goldenDigestFile(version).getName)
      val golden = goldenFile(version)
      val expected = if golden.exists() then IO.read(golden, UTF_8).replace("\r\n", "\n") else ""
      if stale.nonEmpty then
        Result.failure.log(
          s"stale golden files for other format versions: ${stale.mkString(", ")}; delete them"
        )
      else if text != expected then
        Result.failure.log(textMismatch(version, golden, text, expected))
      else
        IO.delete(actualFile(version)) // drop the leftover of a previously failing run

        val digest = binaryDigest()
        val digestFile = goldenDigestFile(version)
        val expectedDigest = if digestFile.exists() then IO.read(digestFile, UTF_8).trim else ""
        if digest != expectedDigest then
          Result.failure.log(digestMismatch(version, digestFile, digest, expectedDigest))
        else Result.success
  end matchesGolden

  private def twice: Result =
    Result.all(List(renderText() ==== renderText(), binaryDigest() ==== binaryDigest()))

  private def windowsRendering: Result =
    // TextSerializer escapes the separator, so on Windows the text arrives with doubled
    // backslashes, while the binary serializer writes the raw bytes.
    val windowsBytes = "\\tmp\\dummy".getBytes(ISO_8859_1)
    val windowsJar = "\\tmp\\dummy\\output.jar".getBytes(ISO_8859_1)
    Result.all(
      List(
        canonicalizeText("  \\\\tmp\\\\dummy") ==== "  /tmp/dummy",
        new String(canonicalizeBinary(windowsBytes), ISO_8859_1) ==== "/tmp/dummy",
        // Byte length must be preserved or the binary format's length prefixes stop matching.
        canonicalizeBinary(windowsBytes).length ==== windowsBytes.length,
        // The jar path must survive too, and must not be truncated by the shorter path's rewrite.
        canonicalizeText("  \\\\tmp\\\\dummy\\\\output.jar") ==== "  /tmp/dummy/output.jar",
        new String(canonicalizeBinary(windowsJar), ISO_8859_1) ==== "/tmp/dummy/output.jar",
      )
    )

  def propCanonicalizeBinaryLength: Property =
    for
      parts <- Gen
        .list(
          Gen.choice1(
            Gen.element1("\\tmp\\dummy", "\\tmp\\dummy\\output.jar", "/tmp/dummy"),
            Gen.string(Gen.latin1, Range.linear(0, 8)),
          ),
          Range.linear(0, 6)
        )
        .forAll
    yield
      val bytes = parts.mkString.getBytes(ISO_8859_1)
      val canonical = new String(canonicalizeBinary(bytes), ISO_8859_1)
      Result.all(
        List(
          canonical.length ==== bytes.length,
          Result.assert(!canonical.contains("\\tmp\\dummy")).log(canonical),
        )
      )
  // Forked tests run in .sbt/matrix/<project>; reach the repo root the way
  // ConsistentAnalysisFormatIntegrationSuite does.
  private val testData = new File("../../../test-data")

  private def goldenFile(version: String) = new File(testData, s"analysis-format-$version.txt")

  private def goldenDigestFile(version: String) =
    new File(testData, s"analysis-format-$version.sha256")

  private def actualFile(version: String) =
    new File(testData, s"analysis-format-$version.actual.txt")

  /**
   * The format writes VERSION first and the constant itself is private, so line 1 of the output
   * is how the suite learns which golden files to compare against.
   */
  private def formatVersion(rendered: String): String =
    rendered.linesIterator.nextOption() match
      case Some(v) if v.nonEmpty && v.forall(_.isDigit) => v
      case other                                        =>
        throw new IllegalStateException(s"expected a format version on line 1, got $other")

  private def textMismatch(version: String, golden: File, actual: String, expected: String) =
    // Report where the new shape landed, but never let a write failure hide the real problem.
    val written =
      try
        IO.write(actualFile(version), actual, UTF_8)
        s"The new shape was written to test-data/${actualFile(version).getName}."
      catch case NonFatal(e) => s"It could not be written to test-data: $e"
    val where = firstDifference(expected, actual).fold("") { case (n, exp, act) =>
      s"\nFirst difference at line $n:\n  expected: $exp\n  actual:   $act"
    }
    if golden.exists() then
      s"""The serialized form of Analysis changed while VERSION is still $version.
         |Bump VERSION in ConsistentAnalysisFormat and add golden files for the new version.
         |${golden.getName} records what $version serializes as; if that version is released,
         |editing the file instead of bumping would hide a format change.
         |$written$where""".stripMargin
    else
      s"""There is no golden file for format version $version.
         |If you just bumped VERSION, add test-data/${golden.getName} plus its .sha256 companion,
         |and delete the ones for the previous version.
         |$written""".stripMargin
  end textMismatch

  private def digestMismatch(version: String, file: File, actual: String, expected: String) =
    if expected.isEmpty then
      s"""There is no recorded binary digest for format version $version.
         |Record it with:
         |  echo $actual > test-data/${file.getName}""".stripMargin
    else
      s"""The binary rendering of Analysis changed while VERSION is still $version.
         |This is the encoding that ships, so it matters even when the text rendering is unchanged.
         |Expected $expected, got $actual.
         |Bump VERSION in ConsistentAnalysisFormat if the change is intended, then record the new
         |digest in test-data/${file.getName}.""".stripMargin

  /** Line number and content of the first differing line, 1-based. */
  private def firstDifference(expected: String, actual: String): Option[(Int, String, String)] =
    val e = expected.linesIterator.toVector
    val a = actual.linesIterator.toVector
    (0 until math.max(e.length, a.length))
      .find(i => e.lift(i) != a.lift(i))
      .map(i => (i + 1, e.lift(i).getOrElse("<no line>"), a.lift(i).getOrElse("<no line>")))

  private def format =
    new ConsistentAnalysisFormat(ReadWriteMappers.getEmptyMappers, reproducible = true)

  private def renderText(): String =
    val out = new ByteArrayOutputStream
    format.write(
      SerializerFactory.text.serializerFor(out),
      AnalysisFormatFixture.analysis,
      AnalysisFormatFixture.setup
    )
    canonicalizeText(new String(out.toByteArray, UTF_8))

  private def binaryDigest(): String =
    val out = new ByteArrayOutputStream
    format.write(
      SerializerFactory.binary.serializerFor(out),
      AnalysisFormatFixture.analysis,
      AnalysisFormatFixture.setup
    )
    val digest = MessageDigest.getInstance("SHA-256").digest(canonicalizeBinary(out.toByteArray))
    HexFormat.of().formatHex(digest)

  /**
   * POSIX renderings of the dummy output paths, which is what the golden files record. Longest
   * first: `/tmp/dummy` is a prefix of `/tmp/dummy/output.jar`, so rewriting the short one first
   * would leave the jar suffix with a stray separator.
   */
  private val dummyPaths =
    List(Analysis.dummyOutputJarPath, Analysis.dummyOutputPath).map(_.toString.replace('\\', '/'))

  /**
   * The dummy output path is the one platform dependent value in the output. Rewrite the Windows
   * rendering to the POSIX one so a single pair of golden files serves every platform. Derived from
   * the POSIX form rather than the running platform, so it can be tested anywhere.
   */
  private def canonicalizeText(text: String): String =
    dummyPaths.foldLeft(text)((acc, p) => acc.replace(p.replace("/", "\\\\"), p))

  /**
   * The binary serializer writes strings raw and length prefixed. ISO-8859-1 maps bytes one to one
   * onto chars, so this rewrite is byte preserving, and the replacement has the same length as the
   * original, so the prefixes stay valid.
   */
  private def canonicalizeBinary(bytes: Array[Byte]): Array[Byte] =
    val latin1 = new String(bytes, ISO_8859_1)
    dummyPaths.foldLeft(latin1)((acc, p) => acc.replace(p.replace('/', '\\'), p)).getBytes(
      ISO_8859_1
    )
end ConsistentAnalysisFormatVersionSuite
