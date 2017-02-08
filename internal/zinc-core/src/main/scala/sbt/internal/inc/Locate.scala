/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt
package internal
package inc

import java.io.File
import java.util.zip.{ ZipException, ZipFile }

import xsbti.compile.{ DefinesClass, PerClasspathEntryLookup }

import Function.const

object Locate {
  /**
   * Right(src) provides the value for the found class
   * Left(true) means that the class was found, but it had no associated value
   * Left(false) means that the class was not found
   */
  def value[S](classpath: Seq[File], get: File => String => Option[S]): String => Either[Boolean, S] =
    {
      val gets = classpath.toStream.map(getValue(get))
      className => find(className, gets)
    }

  def find[S](name: String, gets: Stream[String => Either[Boolean, S]]): Either[Boolean, S] =
    if (gets.isEmpty)
      Left(false)
    else
      gets.head(name) match {
        case Left(false) => find(name, gets.tail)
        case x           => x
      }

  /**
   * Returns a function that searches the provided class path for
   * a class name and returns the entry that defines that class.
   */
  def entry(classpath: Seq[File], lookup: PerClasspathEntryLookup): String => Option[File] =
    {
      val entries = classpath.toStream.map { entry => (entry, lookup.definesClass(entry)) }
      className => entries.collectFirst { case (entry, defines) if defines(className) => entry }
    }
  def resolve(f: File, className: String): File = if (f.isDirectory) classFile(f, className) else f

  def getValue[S](get: File => String => Option[S])(entry: File): String => Either[Boolean, S] =
    {
      val defClass = definesClass(entry)
      val getF = get(entry)
      className => if (defClass(className)) getF(className).toRight(true) else Left(false)
    }

  def definesClass(entry: File): DefinesClass =
    if (entry.isDirectory)
      new DirectoryDefinesClass(entry)
    else if (entry.exists && classpath.ClasspathUtilities.isArchive(entry, contentFallback = true))
      new JarDefinesClass(entry)
    else
      FalseDefinesClass

  private object FalseDefinesClass extends DefinesClass {
    override def apply(binaryClassName: String): Boolean = false
  }

  private class JarDefinesClass(entry: File) extends DefinesClass {
    import collection.JavaConversions._
    private val entries = {
      val jar = try { new ZipFile(entry, ZipFile.OPEN_READ) } catch {
        // ZipException doesn't include the file name :(
        case e: ZipException => throw new RuntimeException("Error opening zip file: " + entry.getName, e)
      }
      try {
        jar.entries.map(e => toClassName(e.getName)).toSet
      } finally {
        jar.close()
      }
    }
    override def apply(binaryClassName: String): Boolean =
      entries.contains(binaryClassName)
  }

  def toClassName(entry: String): String =
    entry.stripSuffix(ClassExt).replace('/', '.')

  val ClassExt = ".class"

  private class DirectoryDefinesClass(entry: File) extends DefinesClass {
    override def apply(binaryClassName: String): Boolean = classFile(entry, binaryClassName).isFile
  }

  def classFile(baseDir: File, className: String): File =
    {
      val (pkg, name) = components(className)
      val dir = subDirectory(baseDir, pkg)
      new File(dir, name + ClassExt)
    }

  def subDirectory(base: File, parts: Seq[String]): File =
    (base /: parts)((b, p) => new File(b, p))

  def components(className: String): (Seq[String], String) =
    {
      assume(!className.isEmpty)
      val parts = className.split("\\.")
      if (parts.length == 1) (Nil, parts(0)) else (parts.init, parts.last)
    }
}
