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

package xsbt

import java.io._
import java.net.URLClassLoader
import java.nio.file.{ Files, Path, Paths }
import java.util.Optional
import java.util.zip.{ GZIPInputStream, GZIPOutputStream }

import sbt.internal.inc._
import sbt.internal.inc.classpath.ClassLoaderCache
import sbt.util.Logger
import xsbti.{ Reporter, ReporterUtil, TestCallback, VirtualFile, VirtualFileRef }
import xsbti.api.ClassLike
import xsbti.compile._

/**
 * A corpus of the `ClassLike`s the Scala 2.13 bridge hands to `AnalysisCallback.api`, with every
 * lazy member forced, for benchmarking and testing the per-class API work Zinc does on them.
 *
 * Usage: `ApiCorpus <out.ser.gz> <classpath> <source dir>...`
 */
object ApiCorpus extends BridgeProviderTestkit {
  def main(args: Array[String]): Unit = {
    val out = Paths.get(args(0))
    val classpath = args(1).split(File.pathSeparator).filter(_.nonEmpty).map(Paths.get(_))
    val sources = args.drop(2).toList.flatMap(d => sourcesIn(Paths.get(d)))
    val apis = compile(sources, classpath.toList)
    write(out, apis)
    println(s"Wrote ${apis.size} classes from ${sources.size} sources to $out")
  }

  def sourcesIn(dir: Path): List[Path] = {
    val stream = Files.walk(dir)
    try {
      stream
        .toArray()
        .toList
        .map(_.asInstanceOf[Path])
        .filter(p => p.toString.endsWith(".scala") || p.toString.endsWith(".java"))
        .sortBy(_.toString)
    } finally stream.close()
  }

  def compile(sources: List[Path], classpath: List[Path]): Vector[ClassLike] = {
    val baseDir = Files.createTempDirectory("api-corpus")
    val noLogger = Logger.Null
    val scalaVersion = ZincBuildInfo.scalaVersion213
    val bridge = getCompilerBridge(baseDir, noLogger, scalaVersion)
    val si = scalaInstance(scalaVersion, baseDir, noLogger)
    val bridgeProvider = ZincUtil.constantBridgeProvider(si, bridge)
    val cache = Some(new ClassLoaderCache(new URLClassLoader(Array())))
    val compiler = new AnalyzingCompiler(
      si,
      bridgeProvider,
      ClasspathOptionsUtil.noboot(si.version),
      _ => (),
      cache
    )
    val converter = PlainVirtualFileConverter.converter
    val targetDir = baseDir.resolve("classes")
    Files.createDirectories(targetDir)
    val callback = new TestCallback {
      override def toVirtualFile(path: Path): VirtualFile = converter.toVirtualFile(path)
    }
    val reporter: Reporter =
      ReporterManager.getReporter(log, ReporterUtil.getDefaultReporterConfig())
    val emptyChanges: DependencyChanges = new DependencyChanges {
      override val modifiedLibraries = new Array[VirtualFileRef](0)
      override val modifiedBinaries = new Array[File](0)
      override val modifiedClasses = new Array[String](0)
      override def isEmpty = true
    }
    compiler.compile(
      sources = sources.map(converter.toVirtualFile(_): VirtualFile).toArray,
      classpath = (classpath :+ targetDir).map(converter.toVirtualFile(_)).toArray,
      converter = converter,
      changes = emptyChanges,
      options = Array("-nowarn"),
      output = CompileOutput(targetDir),
      callback = callback,
      reporter = reporter,
      progressOpt = Optional.empty[CompileProgress],
      log = log
    )
    if (reporter.hasErrors) sys.error("Compilation failed")
    callback.apis.toVector.sortBy(_._1.id).flatMap(_._2.toVector.sortBy(_.name))
  }

  def write(out: Path, apis: Vector[ClassLike]): Unit = {
    val oos = new ObjectOutputStream(
      new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(out)))
    )
    try oos.writeObject(apis.toArray)
    finally oos.close()
  }

  def read(in: Path): Array[ClassLike] = {
    val ois = new ObjectInputStream(
      new GZIPInputStream(new BufferedInputStream(Files.newInputStream(in)))
    )
    try ois.readObject().asInstanceOf[Array[ClassLike]]
    finally ois.close()
  }
}
