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

import Constants._

private[sbt] trait ClassFile {
  val majorVersion: Int
  val minorVersion: Int
  val className: String
  val superClassName: String
  val interfaceNames: Array[String]
  val accessFlags: Int
  val constantPool: Array[Constant]
  val fields: Array[FieldInfo]
  val methods: Array[MethodInfo]
  val attributes: Array[AttributeInfo]
  def sourceFile: Option[String]
  def innerClasses: Array[InnerClassInfo]
  def types: Set[String]
  def stringValue(a: AttributeInfo): String

  // ----- Class-level access flag predicates -----
  // JVMS Table 4.1-A:
  // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-E.1
  def isPublic = (accessFlags & ClassAccessFlags.ACC_PUBLIC) != 0
  def isFinal = (accessFlags & ClassAccessFlags.ACC_FINAL) != 0
  def isInterface = (accessFlags & ClassAccessFlags.ACC_INTERFACE) != 0
  def isAbstract = (accessFlags & ClassAccessFlags.ACC_ABSTRACT) != 0
  def isSynthetic = (accessFlags & ClassAccessFlags.ACC_SYNTHETIC) != 0
  def isAnnotation = (accessFlags & ClassAccessFlags.ACC_ANNOTATION) != 0
  def isEnum = (accessFlags & ClassAccessFlags.ACC_ENUM) != 0

  /**
   * If the given fieldName represents a ConstantValue field, parses its representation from
   * the constant pool and returns it.
   */
  def constantValue(fieldName: String): Option[AnyRef] =
    this.fields
      .find(_.name.exists(_ == fieldName))
      .toSeq
      .flatMap(_.attributes)
      .collectFirst {
        case ai @ classfile.AttributeInfo(Some("ConstantValue"), _) =>
          constantPool(Parser.entryIndex(ai))
      }
      .map {
        case Constant(ConstantString, nextOffset, _, _) =>
          // follow the indirection from ConstantString to ConstantUTF8
          val nextConstant = constantPool(nextOffset)
          nextConstant.value.getOrElse {
            throw new IllegalStateException(s"Empty UTF8 value in constant pool: $nextConstant")
          }
        case constant @ Constant(
              (ConstantFloat | ConstantLong | ConstantDouble | ConstantInteger),
              _,
              _,
              ref
            ) =>
          ref.getOrElse {
            throw new IllegalStateException(s"Empty primitive value in constant pool: $constant")
          }
        case constant =>
          throw new IllegalStateException(s"Unsupported ConstantValue type: $constant")
      }
}

private[sbt] final case class Constant(
    tag: Byte,
    nameIndex: Int,
    typeIndex: Int,
    value: Option[AnyRef]
) {
  def this(tag: Byte, nameIndex: Int, typeIndex: Int) = this(tag, nameIndex, typeIndex, None)
  def this(tag: Byte, nameIndex: Int) = this(tag, nameIndex, -1)
  def this(tag: Byte, value: AnyRef) = this(tag, -1, -1, Some(value))
  def wide = tag == ConstantLong || tag == ConstantDouble

  // Override hashCode to prevent warning with -Ywarn-numeric-widen in Scala 2.10
  // See https://github.com/scala/bug/issues/8340
  override def hashCode: Int =
    37 * (37 * (37 * (37 * (17 + tag.##) + nameIndex.##) + typeIndex.##) + value.##)
}

/**
 * A field entry in a classfile. Predicates read [[FieldAccessFlags]].
 * JVMS Table 4.5-A:
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5-200-A.1
 */
private[sbt] final case class FieldInfo(
    accessFlags: Int,
    name: Option[String],
    descriptor: Option[String],
    attributes: IndexedSeq[AttributeInfo]
) {
  def isPublic = (accessFlags & FieldAccessFlags.ACC_PUBLIC) != 0
  def isPrivate = (accessFlags & FieldAccessFlags.ACC_PRIVATE) != 0
  def isProtected = (accessFlags & FieldAccessFlags.ACC_PROTECTED) != 0
  def isStatic = (accessFlags & FieldAccessFlags.ACC_STATIC) != 0
  def isFinal = (accessFlags & FieldAccessFlags.ACC_FINAL) != 0
  def isVolatile = (accessFlags & FieldAccessFlags.ACC_VOLATILE) != 0
  def isTransient = (accessFlags & FieldAccessFlags.ACC_TRANSIENT) != 0
  def isSynthetic = (accessFlags & FieldAccessFlags.ACC_SYNTHETIC) != 0
  def isEnum = (accessFlags & FieldAccessFlags.ACC_ENUM) != 0
}

/**
 * A method entry in a classfile. Predicates read [[MethodAccessFlags]].
 * JVMS Table 4.6-A:
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
 */
private[sbt] final case class MethodInfo(
    accessFlags: Int,
    name: Option[String],
    descriptor: Option[String],
    attributes: IndexedSeq[AttributeInfo]
) {
  def isPublic = (accessFlags & MethodAccessFlags.ACC_PUBLIC) != 0
  def isPrivate = (accessFlags & MethodAccessFlags.ACC_PRIVATE) != 0
  def isProtected = (accessFlags & MethodAccessFlags.ACC_PROTECTED) != 0
  def isStatic = (accessFlags & MethodAccessFlags.ACC_STATIC) != 0
  def isFinal = (accessFlags & MethodAccessFlags.ACC_FINAL) != 0
  def isSynchronized = (accessFlags & MethodAccessFlags.ACC_SYNCHRONIZED) != 0
  def isBridge = (accessFlags & MethodAccessFlags.ACC_BRIDGE) != 0
  def isVarArgs = (accessFlags & MethodAccessFlags.ACC_VARARGS) != 0
  def isNative = (accessFlags & MethodAccessFlags.ACC_NATIVE) != 0
  def isAbstract = (accessFlags & MethodAccessFlags.ACC_ABSTRACT) != 0
  def isStrict = (accessFlags & MethodAccessFlags.ACC_STRICT) != 0
  def isSynthetic = (accessFlags & MethodAccessFlags.ACC_SYNTHETIC) != 0

  // Name-based — safe by construction.
  def isConstructor = name.exists(_ == "<init>")
  def isStaticInit = name.exists(_ == "<clinit>")
  def isMain =
    isPublic && isStatic && name.contains("main") &&
      descriptor.exists(_ == "([Ljava/lang/String;)V")
}

private[sbt] final case class AttributeInfo(name: Option[String], value: Array[Byte]) {
  def isNamed(s: String) = name.exists(s == _)
  def isSignature = isNamed("Signature")
  def isSourceFile = isNamed("SourceFile")
  def isInnerClasses = isNamed("InnerClasses")
  def isCode = isNamed("Code")
  def isRecord = isNamed("Record")
  def isRuntimeVisibleAnnotations = isNamed("RuntimeVisibleAnnotations")
  def isRuntimeInvisibleAnnotations = isNamed("RuntimeInvisibleAnnotations")
  def isRuntimeVisibleParameterAnnotations = isNamed("RuntimeVisibleParameterAnnotations")
  def isRuntimeInvisibleParameterAnnotations = isNamed("RuntimeInvisibleParameterAnnotations")
  def isRuntimeVisibleTypeAnnotations = isNamed("RuntimeVisibleTypeAnnotations")
  def isRuntimeInvisibleTypeAnnotations = isNamed("RuntimeInvisibleTypeAnnotations")
  def isAnnotationDefault = isNamed("AnnotationDefault")
}

/**
 * An entry in a classfile's `InnerClasses` attribute. Predicates read
 * [[InnerClassAccessFlags]]. JVMS Table 4.7.6-A:
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.6-300-D.2-5
 */
private[sbt] final case class InnerClassInfo(
    accessFlags: Int,
    innerName: Option[String],
    innerClassName: String,
    outerClassName: String
) {
  def isPublic = (accessFlags & InnerClassAccessFlags.ACC_PUBLIC) != 0
  def isPrivate = (accessFlags & InnerClassAccessFlags.ACC_PRIVATE) != 0
  def isProtected = (accessFlags & InnerClassAccessFlags.ACC_PROTECTED) != 0
  def isStatic = (accessFlags & InnerClassAccessFlags.ACC_STATIC) != 0
  def isFinal = (accessFlags & InnerClassAccessFlags.ACC_FINAL) != 0
  def isInterface = (accessFlags & InnerClassAccessFlags.ACC_INTERFACE) != 0
  def isAbstract = (accessFlags & InnerClassAccessFlags.ACC_ABSTRACT) != 0
  def isSynthetic = (accessFlags & InnerClassAccessFlags.ACC_SYNTHETIC) != 0
  def isAnnotation = (accessFlags & InnerClassAccessFlags.ACC_ANNOTATION) != 0
  def isEnum = (accessFlags & InnerClassAccessFlags.ACC_ENUM) != 0
}

/**
 * Class access and property flags. JVMS §4.1, Table 4.1-A.
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-E.1
 */
private[sbt] object ClassAccessFlags {
  final val ACC_PUBLIC = 0x0001
  final val ACC_FINAL = 0x0010
  final val ACC_SUPER = 0x0020
  final val ACC_INTERFACE = 0x0200
  final val ACC_ABSTRACT = 0x0400
  final val ACC_SYNTHETIC = 0x1000
  final val ACC_ANNOTATION = 0x2000
  final val ACC_ENUM = 0x4000
}

/**
 * Field access and property flags. JVMS §4.5, Table 4.5-A.
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5-200-A.1
 */
private[sbt] object FieldAccessFlags {
  final val ACC_PUBLIC = 0x0001
  final val ACC_PRIVATE = 0x0002
  final val ACC_PROTECTED = 0x0004
  final val ACC_STATIC = 0x0008
  final val ACC_FINAL = 0x0010
  final val ACC_VOLATILE = 0x0040
  final val ACC_TRANSIENT = 0x0080
  final val ACC_SYNTHETIC = 0x1000
  final val ACC_ENUM = 0x4000
}

/**
 * Method access and property flags. JVMS §4.6, Table 4.6-A.
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
 */
private[sbt] object MethodAccessFlags {
  final val ACC_PUBLIC = 0x0001
  final val ACC_PRIVATE = 0x0002
  final val ACC_PROTECTED = 0x0004
  final val ACC_STATIC = 0x0008
  final val ACC_FINAL = 0x0010
  final val ACC_SYNCHRONIZED = 0x0020
  final val ACC_BRIDGE = 0x0040
  final val ACC_VARARGS = 0x0080
  final val ACC_NATIVE = 0x0100
  final val ACC_ABSTRACT = 0x0400
  final val ACC_STRICT = 0x0800
  final val ACC_SYNTHETIC = 0x1000
}

/**
 * Inner class access and property flags. JVMS §4.7.6, Table 4.7.6-A.
 * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.6-300-D.2-5
 */
private[sbt] object InnerClassAccessFlags {
  final val ACC_PUBLIC = 0x0001
  final val ACC_PRIVATE = 0x0002
  final val ACC_PROTECTED = 0x0004
  final val ACC_STATIC = 0x0008
  final val ACC_FINAL = 0x0010
  final val ACC_INTERFACE = 0x0200
  final val ACC_ABSTRACT = 0x0400
  final val ACC_SYNTHETIC = 0x1000
  final val ACC_ANNOTATION = 0x2000
  final val ACC_ENUM = 0x4000
}

private[sbt] object Constants {
  final val JavaMagic = 0xcafebabe
  final val ConstantUTF8 = 1
  final val ConstantUnicode = 2
  final val ConstantInteger = 3
  final val ConstantFloat = 4
  final val ConstantLong = 5
  final val ConstantDouble = 6
  final val ConstantClass = 7
  final val ConstantString = 8
  final val ConstantField = 9
  final val ConstantMethod = 10
  final val ConstantInterfaceMethod = 11
  final val ConstantNameAndType = 12
  final val ConstantMethodHandle = 15
  final val ConstantMethodType = 16
  final val ConstantInvokeDynamic = 18
  final val ConstantModule = 19
  final val ConstantPackage = 20
  final val ConstantDynamic = 17 // http://openjdk.java.net/jeps/309
  final val ClassDescriptor = 'L'
}
