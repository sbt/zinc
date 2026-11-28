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

package sbt.internal.inc

import java.io.File
import java.nio.file.Paths

import sbt.internal.scripted.FileCommands
import sbt.io.IO

import xsbti.Logger

class ZincFileCommands(baseDirectory: File, logger: Logger) extends FileCommands(baseDirectory):
  override def commandMap: Map[String, List[String] => Unit] =
    (super.commandMap + {
      "pause" noArg {
        // Redefine pause not to use `System.console`, which is too restrictive
        println(s"Pausing in $baseDirectory (press enter to continue)")
        scala.io.StdIn.readLine()
        println("Restarting the execution.")
      }
    } + ("generate-java" -> generateJava)).map {
      case (cmd, fn) =>
        cmd ->
          ((args: List[String]) =>
            logger.debug(() => s"Running: $$ $cmd ${args.mkString(" ")}")
            fn(args)
          )
    }

  /**
   * `generate-java <path> <classes> <methods>` writes a Java class named after the file, with
   * `classes` nested classes of `methods` methods each: Scala 3's Java parser overflows its stack
   * on a class with thousands of members.
   */
  private def generateJava(args: List[String]): Unit = args match
    case List(path, classes, methods) =>
      val file = fromString(path)
      val out = StringBuilder(s"public class ${file.getName.stripSuffix(".java")} {\n")
      for c <- 1 to classes.toInt do
        out ++= s"  public static class Nested$c {\n"
        for m <- 1 to methods.toInt do
          out ++= s"    public int method${m}WithAVeryLongNameToInflateTheTasty"
          out ++= s"(int argumentNumberOne, String argumentNumberTwo) { return $m; }\n"
        out ++= "  }\n"
      out ++= "}\n"
      IO.write(file, out.result())
    case _ => scriptError(s"Expected generate-java <path> <classes> <methods>, got: $args")

  override def absent(paths: List[String]): Unit =
    val present = paths.filter(exists)
    if present.nonEmpty then
      scriptError("File(s) existed: " + present.mkString("[ ", " , ", " ]"))

  override def newer(a: String, b: String): Unit =
    val isNewer = exists(a) && (!exists(b) || getModifiedTimeOrZero(a) > getModifiedTimeOrZero(b))
    if !isNewer then
      scriptError(s"$a is not newer than $b")

  override def exists(paths: List[String]): Unit =
    val notPresent = paths.filterNot(exists)
    if notPresent.nonEmpty then
      scriptError("File(s) did not exist: " + notPresent.mkString("[ ", " , ", " ]"))

  private def exists(path: String): Boolean =
    pathFold(path)(_.exists(), JarUtils.exists)(_ || _)

  private def getModifiedTimeOrZero(path: String): Long =
    pathFold(path)(IO.getModifiedTimeOrZero, JarUtils.readModifiedTime)(_ max _)

  /**
   * Folds over representations of path (analogously to Either#fold).
   * Path can be a path to regular file or a jared class.
   *
   * This method is pretty hacky but makes scripted tests work with
   * Straight to Jar compilation and without changing assertions there.
   * The path will always point to actual file, but this method will
   * attempt to run a function for both plain file and jared class
   * and then decide which result is relevant with `combine` function.
   *
   * As an example, it will convert path "target/classes/C.class" to
   *   "/tmp/sbt_535fddcd/target/classes/a/b/c/C.class"
   * as well as to
   *   "/tmp/sbt_535fddcd/target/classes/output.jar!a/b/c/C.class"
   * and run functions on them, e.g. checking if one of those files exists.
   */
  private def pathFold[A](path: String)(
      transformPlain: File => A,
      transformJared: JarUtils.ClassInJar => A
  )(combine: (A, A) => A): A =
    val jaredRes =
      val relBasePath = "target/classes"
      IO.relativize(new File(relBasePath), new File(path)).map { relClass =>
        val jar = Paths.get(baseDirectory.toString, relBasePath, "output.jar").toFile
        transformJared(JarUtils.ClassInJar(jar.toPath, relClass))
      }
    val regularRes = transformPlain(fromString(path))
    jaredRes.map(combine(_, regularRes)).getOrElse(regularRes)
end ZincFileCommands
