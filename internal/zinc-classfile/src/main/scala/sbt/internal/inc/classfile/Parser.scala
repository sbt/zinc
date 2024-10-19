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

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.io.{ BufferedInputStream, InputStream, File, DataInputStream }

import sbt.internal.io.ErrorHandling

import scala.annotation.{ switch, tailrec }
import sbt.io.Using
import sbt.util.Logger

// Loosely based on jdepend.framework.ClassFileParser by Mike Clark, Clarkware Consulting, Inc.
// (BSD license at time of initial port; MIT license as of 2022)
//
// Note that unlike the rest of sbt, some things might be null.
//
// For debugging this, it's useful to uncomment this:
//   logger.setLevel(sbt.util.Level.Debug)
// in ParserSpecification and/or JavaCompilerForUnitTesting

import Constants._

private[sbt] object Parser {

  def apply(file: Path, log: Logger): ClassFile =
    Using.bufferedInputStream(Files.newInputStream(file))(parse(file.toString, log)).toOption.get

  def apply(file: File, log: Logger): ClassFile =
    Using.fileInputStream(file)(parse(file.toString, log)).toOption.get

  def apply(url: URL, log: Logger): ClassFile =
    usingUrlInputStreamWithoutCaching(url)(parse(url.toString, log)).toOption.get

  // JarURLConnection with caching enabled will never close the jar
  private val usingUrlInputStreamWithoutCaching = Using.resource((u: URL) =>
    ErrorHandling.translate("Error opening " + u + ": ") {
      val urlConnection = u.openConnection()
      urlConnection.setUseCaches(false)
      new BufferedInputStream(urlConnection.getInputStream())
    }
  )

  private def parse(readableName: String, log: Logger)(is: InputStream): Either[String, ClassFile] =
    Right(parseImpl(readableName, log, is))
  private def parseImpl(readableName: String, log: Logger, is: InputStream): ClassFile = {
    val in = new DataInputStream(is)
    assume(in.readInt() == JavaMagic, "Invalid class file: " + readableName)

    new ClassFile {
      val minorVersion: Int = in.readUnsignedShort()
      val majorVersion: Int = in.readUnsignedShort()

      val constantPool = parseConstantPool(in)
      val accessFlags: Int = in.readUnsignedShort()

      val className = getClassConstantName(in.readUnsignedShort())
      log.debug(s"[zinc] classfile.Parser parsing $className")
      val superClassName = getClassConstantName(in.readUnsignedShort())
      val interfaceNames =
        array(in.readUnsignedShort())(getClassConstantName(in.readUnsignedShort()))

      val fields = readFieldsOrMethods()
      val methods = readFieldsOrMethods()

      val attributes = array(in.readUnsignedShort())(parseAttribute())

      lazy val sourceFile =
        for (sourceFileAttribute <- attributes.find(_.isSourceFile))
          yield toUTF8(entryIndex(sourceFileAttribute))

      def stringValue(a: AttributeInfo) = toUTF8(entryIndex(a))

      private def readFieldsOrMethods() = array(in.readUnsignedShort())(parseFieldOrMethodInfo())
      private def toUTF8(entryIndex: Int) = {
        val entry = constantPool(entryIndex)
        assume(entry.tag == ConstantUTF8, "Constant pool entry is not a UTF8 type: " + entryIndex)
        entry.value.get.asInstanceOf[String]
      }
      private def getClassConstantName(entryIndex: Int) = {
        val entry = constantPool(entryIndex)
        if (entry == null) ""
        else slashesToDots(toUTF8(entry.nameIndex))
      }
      private def toString(index: Int) = {
        if (index <= 0) None
        else Some(toUTF8(index))
      }
      private def parseFieldOrMethodInfo() =
        FieldOrMethodInfo(
          in.readUnsignedShort(),
          toString(in.readUnsignedShort()),
          toString(in.readUnsignedShort()),
          array(in.readUnsignedShort())(parseAttribute()).toIndexedSeq
        )
      private def parseAttribute() = {
        val nameIndex = in.readUnsignedShort()
        val name = if (nameIndex == -1) None else Some(toUTF8(nameIndex))
        val value = array(in.readInt())(in.readByte())
        AttributeInfo(name, value)
      }

      def types: Set[String] = {
        // support for parsing annotations in Java classfiles is new in sbt 1.7.0.  as of July 2022
        // we don't completely trust it yet (one bug already forced us to build sbt 1.7.1) so if
        // that part of the parser blows up, we only warn rather than failing compilation. this runs
        // the risk of the warning not being noticed and the bug not being reported, but so be it.
        // (for the time being, at least!)
        def annotationsReferencesCarefully =
          try annotationsReferences
          catch {
            case re: RuntimeException =>
              log.warn(s"couldn't parse annotations in $readableName ($re)")
              List()
          }
        // the other aspects of classfile.Parser are long battle-tested
        (classConstantReferences ++ fieldTypes ++ methodTypes ++ annotationsReferencesCarefully).toSet
      }

      private def getTypes(fieldsOrMethods: Array[FieldOrMethodInfo]) =
        fieldsOrMethods.flatMap { fieldOrMethod =>
          descriptorToTypes(fieldOrMethod.descriptor)
        }

      private def fieldTypes = getTypes(fields)
      private def methodTypes = getTypes(methods)

      private def annotationsReferences: List[String] = {
        val allAttributes =
          attributes ++
            fields.flatMap(_.attributes) ++
            methods.flatMap(_.attributes)
        allAttributes
          .filter(x => x.isRuntimeVisibleAnnotations || x.isRuntimeInvisibleAnnotations)
          .flatMap { attr =>
            val in = new DataInputStream(new java.io.ByteArrayInputStream(attr.value))
            val numAnnotations = in.readUnsignedShort()
            val result = collection.mutable.ListBuffer[String]()
            def parseElementValue(): Unit = { // JVMS 4.7.16.1
              val c = in.readUnsignedByte().toChar
              c match {
                case 'e' =>
                  result += slashesToDots(toUTF8(in.readUnsignedShort()))
                  val _ = in.readUnsignedShort()
                case 'c' =>
                  result += slashesToDots(toUTF8(in.readUnsignedShort()))
                case '@' =>
                  parseAnnotation()
                case '[' =>
                  for (_ <- 0 until in.readUnsignedShort())
                    parseElementValue()
                case 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z' | 's' =>
                  val _ = in.readUnsignedShort()
                case _ =>
                  // if we see something unexpected, we're likely already doomed and trying to
                  // continue parsing will just make troubleshooting harder. so let's bail
                  sys.error(s"unexpected tag in annotation: '$c'")
              }
            }
            def parseAnnotation(): Unit = { // JVMS 4.7.16
              result += slashesToDots(toUTF8(in.readUnsignedShort()))
              for (_ <- 0 until in.readUnsignedShort()) {
                in.readUnsignedShort() // skip element name index
                parseElementValue()
              }
            }
            for (_ <- 0 until numAnnotations)
              parseAnnotation()
            // the type names in the constant pool have the form e.g. `Ljava/lang/Object;`;
            // we've already used `slashesToDots`, but we still need to drop the `L` and the semicolon
            result.map(name => name.slice(1, name.length - 1))
          }.toList
      }

      private def classConstantReferences =
        constants.flatMap { constant =>
          constant.tag match {
            case ConstantClass =>
              val name = toUTF8(constant.nameIndex)
              if (name.startsWith("["))
                descriptorToTypes(Some(name))
              else
                slashesToDots(name) :: Nil
            case _ => Nil
          }
        }
      private def constants = {
        @tailrec
        def next(i: Int, list: List[Constant]): List[Constant] = {
          if (i < constantPool.length) {
            val constant = constantPool(i)
            next(if (constant.wide) i + 2 else i + 1, constant :: list)
          } else
            list
        }
        next(1, Nil)
      }
    }
  }
  private def array[T: scala.reflect.ClassTag](size: Int)(f: => T) = Array.tabulate(size)(_ => f)
  private def parseConstantPool(in: DataInputStream) = {
    val constantPoolSize = in.readUnsignedShort()
    val pool = new Array[Constant](constantPoolSize)

    @tailrec
    def parse(i: Int): Unit =
      if (i < constantPoolSize) {
        val constant = getConstant(in)
        pool(i) = constant
        parse(if (constant.wide) i + 2 else i + 1)
      }

    parse(1)
    pool
  }

  private def getConstant(in: DataInputStream): Constant = {
    val tag = in.readByte()

    // No switch for byte scrutinees! Stupid compiler.
    (tag.toInt: @switch) match {
      case ConstantClass | ConstantString => new Constant(tag, in.readUnsignedShort())
      case ConstantField | ConstantMethod | ConstantInterfaceMethod | ConstantNameAndType =>
        new Constant(tag, in.readUnsignedShort(), in.readUnsignedShort())
      case ConstantInteger => new Constant(tag, java.lang.Integer.valueOf(in.readInt()))
      case ConstantFloat   => new Constant(tag, java.lang.Float.valueOf(in.readFloat()))
      case ConstantLong    => new Constant(tag, java.lang.Long.valueOf(in.readLong()))
      case ConstantDouble  => new Constant(tag, java.lang.Double.valueOf(in.readDouble()))
      case ConstantUTF8    => new Constant(tag, in.readUTF())
      // TODO: proper support
      case ConstantMethodHandle =>
        in.readByte()
        in.readUnsignedShort()
        Constant(tag, -1, -1, None)
      case ConstantMethodType =>
        in.readUnsignedShort()
        Constant(tag, -1, -1, None)
      case ConstantInvokeDynamic =>
        in.readUnsignedShort()
        in.readUnsignedShort()
        Constant(tag, -1, -1, None)
      case ConstantModule =>
        in.readUnsignedShort()
        Constant(tag, -1, -1, None)
      case ConstantPackage =>
        in.readUnsignedShort()
        Constant(tag, -1, -1, None)
      case ConstantDynamic =>
        in.readUnsignedShort()
        in.readUnsignedShort()
        Constant(tag, -1, -1, None)
      case _ => sys.error("Unknown constant: " + tag)
    }
  }

  private def toInt(v: Byte) = if (v < 0) v + 256 else v.toInt
  private def u2(highByte: Byte, lowByte: Byte): Int =
    toInt(highByte) * 256 + toInt(lowByte)
  def entryIndex(a: AttributeInfo) =
    a.value match {
      case Array(v0, v1) =>
        u2(v0, v1)
      case _ =>
        sys.error(s"Expected two bytes for unsigned value; got: ${a.value.length}")
    }

  private def slashesToDots(s: String) = s.replace('/', '.')

  private def descriptorToTypes(descriptor: Option[String]) = {
    @tailrec
    def toTypes(descriptor: String, types: List[String]): List[String] = {
      val startIndex = descriptor.indexOf(ClassDescriptor)
      if (startIndex < 0)
        types
      else {
        val endIndex = descriptor.indexOf(';', startIndex + 1)
        val tpe = slashesToDots(descriptor.substring(startIndex + 1, endIndex))
        toTypes(descriptor.substring(endIndex), tpe :: types)
      }
    }
    toTypes(descriptor.getOrElse(""), Nil)
  }
}
