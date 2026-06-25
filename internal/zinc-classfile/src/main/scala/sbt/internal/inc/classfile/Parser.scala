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
import java.io.{ BufferedInputStream, ByteArrayInputStream, InputStream, File, DataInputStream }

import sbt.internal.io.ErrorHandling

import scala.annotation.{ switch, tailrec }
import scala.util.control.NonFatal
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

      lazy val innerClasses: Array[InnerClassInfo] = {
        attributes
          .find(_.isInnerClasses)
          .map(parseInnerClasses)
          .getOrElse(Array.empty)
      }

      private def parseInnerClasses(a: AttributeInfo): Array[InnerClassInfo] = {
        val bais = new ByteArrayInputStream(a.value)
        val data = new DataInputStream(bais)
        val numberOfClasses = data.readUnsignedShort()
        Array.tabulate(numberOfClasses) { _ =>
          val innerClassInfoIndex = data.readUnsignedShort()
          val outerClassInfoIndex = data.readUnsignedShort()
          val innerNameIndex = data.readUnsignedShort()
          val innerClassAccessFlags = data.readUnsignedShort()
          val innerName = if (innerNameIndex == 0) None else Some(toUTF8(innerNameIndex))
          val innerCN =
            if (innerClassInfoIndex == 0) ""
            else getClassConstantName(innerClassInfoIndex)
          val outerCN =
            if (outerClassInfoIndex == 0) ""
            else getClassConstantName(outerClassInfoIndex)
          InnerClassInfo(innerClassAccessFlags, innerName, innerCN, outerCN)
        }
      }

      override lazy val sourceFile: Option[String] =
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
            // NonFatal (not just RuntimeException): the nested Code/Record parsing reads raw byte
            // counts, so malformed input can surface as EOFException etc. — still only warn.
            case NonFatal(e) =>
              log.warn(s"couldn't parse annotations in $readableName ($e)")
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
        // Annotation metadata lives in several attribute kinds, some nested: type annotations on
        // locals/casts/etc. sit inside a method's `Code` attribute (JVMS 4.7.3), and annotations on
        // record components sit inside the class's `Record` attribute (JVMS 4.7.30). Surface those
        // nested attributes alongside the top-level ones, then dispatch each to the parser for its
        // shape.
        val nestedCodeAttributes =
          methods.flatMap(_.attributes).filter(_.isCode).flatMap(nestedAttributes)
        val nestedRecordAttributes =
          attributes.filter(_.isRecord).flatMap(recordComponentAttributes)
        val allAttributes =
          attributes ++
            fields.flatMap(_.attributes) ++
            methods.flatMap(_.attributes) ++
            nestedCodeAttributes ++
            nestedRecordAttributes
        allAttributes.flatMap(attributeReferences).toList
      }

      /** Type names referenced by a single annotation-bearing attribute (empty for any other). */
      private def attributeReferences(attr: AttributeInfo): List[String] =
        if (attr.isRuntimeVisibleAnnotations || attr.isRuntimeInvisibleAnnotations)
          parseDeclarationAnnotations(attr.value) // JVMS 4.7.16
        else if (
          attr.isRuntimeVisibleParameterAnnotations || attr.isRuntimeInvisibleParameterAnnotations
        )
          parseParameterAnnotations(attr.value) // JVMS 4.7.18
        else if (attr.isRuntimeVisibleTypeAnnotations || attr.isRuntimeInvisibleTypeAnnotations)
          parseTypeAnnotations(attr.value) // JVMS 4.7.20
        else if (attr.isAnnotationDefault)
          parseAnnotationDefault(attr.value) // JVMS 4.7.22
        else
          Nil

      // --- entry parsers: one per attribute shape, all over the shared body parsing below --------

      /** RuntimeVisible/InvisibleAnnotations (JVMS 4.7.16): a count, then that many annotations. */
      private def parseDeclarationAnnotations(value: Array[Byte]): List[String] =
        withAnnotationStream(value)((in, result) => parseAnnotations(in, result))

      /** RuntimeVisible/InvisibleParameterAnnotations (JVMS 4.7.18): a `u1 num_parameters` count,
       *  each parameter then carrying its own `u2 num_annotations`. */
      private def parseParameterAnnotations(value: Array[Byte]): List[String] =
        withAnnotationStream(value) { (in, result) =>
          val numParameters = in.readUnsignedByte()
          for (_ <- 0 until numParameters)
            parseAnnotations(in, result)
        }

      /** RuntimeVisible/InvisibleTypeAnnotations (JVMS 4.7.20): `u2 num_annotations`, each a
       *  `type_annotation` whose `target_info`/`type_path` prefix we skip before the annotation. */
      private def parseTypeAnnotations(value: Array[Byte]): List[String] =
        withAnnotationStream(value) { (in, result) =>
          for (_ <- 0 until in.readUnsignedShort()) {
            skipTypeAnnotationTarget(in)
            parseAnnotation(in, result)
          }
        }

      /** AnnotationDefault (JVMS 4.7.22): a single `element_value` (the default for an `@interface`
       *  element), which may itself reference enum/class/nested-annotation types. */
      private def parseAnnotationDefault(value: Array[Byte]): List[String] =
        withAnnotationStream(value)((in, result) => parseElementValue(in, result))

      /** Sets up the stream/buffer and decodes the collected raw descriptors into type names via
       *  [[descriptorToTypes]]. The collected values are JVM descriptors (annotation/enum types are
       *  always `L...;`, but a `class` element value may be an array `[L...;` or a primitive like
       *  `I`), so we decode rather than blindly strip — else array class literals leak a
       *  `L`-prefixed name and primitives produce empty ones. */
      private def withAnnotationStream(
          value: Array[Byte]
      )(f: (DataInputStream, collection.mutable.ListBuffer[String]) => Unit): List[String] = {
        val in = new DataInputStream(new ByteArrayInputStream(value))
        val result = collection.mutable.ListBuffer[String]()
        f(in, result)
        result.flatMap(descriptor => descriptorToTypes(Some(descriptor))).toList
      }

      // --- shared annotation-body parsing (JVMS 4.7.16) ------------------------------------------

      private def parseAnnotations(
          in: DataInputStream,
          result: collection.mutable.ListBuffer[String]
      ): Unit =
        for (_ <- 0 until in.readUnsignedShort())
          parseAnnotation(in, result)

      private def parseAnnotation(
          in: DataInputStream,
          result: collection.mutable.ListBuffer[String]
      ): Unit = { // JVMS 4.7.16
        result += toUTF8(in.readUnsignedShort()) // type_index (a field descriptor)
        for (_ <- 0 until in.readUnsignedShort()) {
          in.readUnsignedShort() // skip element name index
          parseElementValue(in, result)
        }
      }

      private def parseElementValue(
          in: DataInputStream,
          result: collection.mutable.ListBuffer[String]
      ): Unit = { // JVMS 4.7.16.1
        val c = in.readUnsignedByte().toChar
        c match {
          case 'e' =>
            result += toUTF8(in.readUnsignedShort()) // enum type_name_index (a field descriptor)
            val _ = in.readUnsignedShort() // const_name_index
          case 'c' =>
            result += toUTF8(in.readUnsignedShort()) // class_info_index (a return descriptor)
          case '@' =>
            parseAnnotation(in, result)
          case '[' =>
            for (_ <- 0 until in.readUnsignedShort())
              parseElementValue(in, result)
          case 'B' | 'C' | 'D' | 'F' | 'I' | 'J' | 'S' | 'Z' | 's' =>
            val _ = in.readUnsignedShort()
          case _ =>
            // if we see something unexpected, we're likely already doomed and trying to
            // continue parsing will just make troubleshooting harder. so let's bail
            sys.error(s"unexpected tag in annotation: '$c'")
        }
      }

      /** Skips a `type_annotation`'s `target_info` and `type_path` (JVMS 4.7.20.1/4.7.20.2): they
       *  locate where the annotation applies and carry no type references, so we only need to
       *  consume them so the following annotation body is read from the right offset. */
      private def skipTypeAnnotationTarget(in: DataInputStream): Unit = {
        in.readUnsignedByte() match { // target_type, JVMS Table 4.7.20-A/B
          case 0x00 | 0x01 => in.readUnsignedByte() // type_parameter_target
          case 0x10        => in.readUnsignedShort() // supertype_target
          case 0x11 | 0x12 => // type_parameter_bound_target
            in.readUnsignedByte(); in.readUnsignedByte()
          case 0x13 | 0x14 | 0x15 => () // empty_target
          case 0x16               => in.readUnsignedByte() // formal_parameter_target
          case 0x17               => in.readUnsignedShort() // throws_target
          case 0x40 | 0x41 => // localvar_target
            for (_ <- 0 until in.readUnsignedShort()) {
              in.readUnsignedShort(); in.readUnsignedShort(); in.readUnsignedShort()
            }
          case 0x42                      => in.readUnsignedShort() // catch_target
          case 0x43 | 0x44 | 0x45 | 0x46 => in.readUnsignedShort() // offset_target
          case 0x47 | 0x48 | 0x49 | 0x4a | 0x4b => // type_argument_target
            in.readUnsignedShort(); in.readUnsignedByte()
          case other => sys.error(s"unexpected type annotation target_type: 0x${other.toHexString}")
        }
        for (_ <- 0 until in.readUnsignedByte()) { // type_path: u1 path_length, then path entries
          in.readUnsignedByte(); in.readUnsignedByte()
        }
      }

      // --- nested attribute containers (Code / Record) -------------------------------------------

      /** The nested attributes carried in a method's `Code` attribute (JVMS 4.7.3) — where type
       *  annotations on locals, casts, `instanceof`, `new`, etc. live. */
      private def nestedAttributes(code: AttributeInfo): Array[AttributeInfo] = {
        val in = new DataInputStream(new ByteArrayInputStream(code.value))
        in.readUnsignedShort() // max_stack
        in.readUnsignedShort() // max_locals
        skipFully(in, in.readInt()) // code[code_length]
        // exception_table: each entry is 4 × u2 (start_pc, end_pc, handler_pc, catch_type)
        for (_ <- 0 until in.readUnsignedShort()) skipFully(in, 8)
        readNestedAttributes(in)
      }

      /** The attributes of every `record_component_info` in a class's `Record` attribute (JVMS
       *  4.7.30) — where annotations targeting record components live. */
      private def recordComponentAttributes(record: AttributeInfo): Array[AttributeInfo] = {
        val in = new DataInputStream(new ByteArrayInputStream(record.value))
        val componentsCount = in.readUnsignedShort()
        Array
          .fill(componentsCount) {
            in.readUnsignedShort() // name_index
            in.readUnsignedShort() // descriptor_index
            readNestedAttributes(in)
          }
          .flatten
      }

      /** Reads an `attributes_count`-prefixed `attribute_info[]` block, materializing only the
       *  annotation-bearing attributes (those [[attributeReferences]] consumes) and skipping the
       *  rest in-stream. A `Code` attribute carries `LineNumberTable`, `StackMapTable`, etc. for
       *  every method; copying those bytes (already held in the enclosing value) just to discard
       *  them would be pure waste. */
      private def readNestedAttributes(in: DataInputStream): Array[AttributeInfo] = {
        val count = in.readUnsignedShort()
        val attrs = collection.mutable.ArrayBuffer.empty[AttributeInfo]
        for (_ <- 0 until count) {
          val name = toUTF8(in.readUnsignedShort())
          val length = in.readInt()
          if (isAnnotationAttributeName(name)) {
            val value = new Array[Byte](length)
            in.readFully(value)
            attrs += AttributeInfo(Some(name), value)
          } else
            skipFully(in, length)
        }
        attrs.toArray
      }

      /** The attribute names [[attributeReferences]] knows how to extract type references from. */
      private def isAnnotationAttributeName(name: String): Boolean =
        name == "RuntimeVisibleAnnotations" || name == "RuntimeInvisibleAnnotations" ||
          name == "RuntimeVisibleParameterAnnotations" ||
          name == "RuntimeInvisibleParameterAnnotations" ||
          name == "RuntimeVisibleTypeAnnotations" || name == "RuntimeInvisibleTypeAnnotations" ||
          name == "AnnotationDefault"

      private def skipFully(in: DataInputStream, n: Int): Unit = {
        var remaining = n
        while (remaining > 0) {
          val skipped = in.skipBytes(remaining)
          if (skipped <= 0) { in.readByte(); remaining -= 1 }
          else remaining -= skipped
        }
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
