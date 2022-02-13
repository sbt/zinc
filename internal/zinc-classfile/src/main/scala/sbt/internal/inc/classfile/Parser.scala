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

package sbt
package internal
package inc
package classfile

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.io.{ BufferedInputStream, InputStream, File, DataInputStream }

import sbt.internal.io.ErrorHandling

import scala.annotation.switch
import sbt.io.Using

// Translation of jdepend.framework.ClassFileParser by Mike Clark, Clarkware Consulting, Inc.
// BSD Licensed
//
// Note that unlike the rest of sbt, some things might be null.

import Constants._

private[sbt] object Parser {
  def apply(file: Path): ClassFile =
    Using.bufferedInputStream(Files.newInputStream(file))(parse(file.toString)).right.get

  def apply(file: File): ClassFile =
    Using.fileInputStream(file)(parse(file.toString)).right.get

  def apply(url: URL): ClassFile =
    usingUrlInputStreamWithoutCaching(url)(parse(url.toString)).right.get

  // JarURLConnection with caching enabled will never close the jar
  private val usingUrlInputStreamWithoutCaching = Using.resource((u: URL) =>
    ErrorHandling.translate("Error opening " + u + ": ") {
      val urlConnection = u.openConnection()
      urlConnection.setUseCaches(false)
      new BufferedInputStream(urlConnection.getInputStream())
    }
  )

  private def parse(readableName: String)(is: InputStream): Either[String, ClassFile] =
    Right(parseImpl(readableName, is))
  private def parseImpl(readableName: String, is: InputStream): ClassFile = {
    val in = new DataInputStream(is)
    assume(in.readInt() == JavaMagic, "Invalid class file: " + readableName)

    new ClassFile {
      val minorVersion: Int = in.readUnsignedShort()
      val majorVersion: Int = in.readUnsignedShort()

      val constantPool = parseConstantPool(in)
      val accessFlags: Int = in.readUnsignedShort()

      val className = getClassConstantName(in.readUnsignedShort())
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
          array(in.readUnsignedShort())(parseAttribute())
        )
      private def parseAttribute() = {
        val nameIndex = in.readUnsignedShort()
        val name = if (nameIndex == -1) None else Some(toUTF8(nameIndex))
        val value = array(in.readInt())(in.readByte())
        AttributeInfo(name, value)
      }

      def types = (classConstantReferences ++ fieldTypes ++ methodTypes).toSet

      private def getTypes(fieldsOrMethods: Array[FieldOrMethodInfo]) =
        fieldsOrMethods.flatMap { fieldOrMethod =>
          descriptorToTypes(fieldOrMethod.descriptor)
        }

      private def fieldTypes = getTypes(fields)
      private def methodTypes = getTypes(methods)

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
  private def array[T: scala.reflect.Manifest](size: Int)(f: => T) = Array.tabulate(size)(_ => f)
  private def parseConstantPool(in: DataInputStream) = {
    val constantPoolSize = in.readUnsignedShort()
    val pool = new Array[Constant](constantPoolSize)

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
  def entryIndex(a: AttributeInfo) = {
    require(a.value.length == 2, s"Expected two bytes for unsigned value; got: ${a.value.length}")
    val Array(v0, v1) = a.value
    toInt(v0) * 256 + toInt(v1)
  }

  private def slashesToDots(s: String) = s.replace('/', '.')

  private def descriptorToTypes(descriptor: Option[String]) = {
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
