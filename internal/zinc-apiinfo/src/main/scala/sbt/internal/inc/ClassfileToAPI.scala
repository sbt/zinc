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

import java.io.{ ByteArrayInputStream, DataInputStream }
import java.lang.reflect.Modifier
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal
import xsbti.api
import xsbti.api.SafeLazyProxy
import sbt.internal.inc.classfile.{ AttributeInfo, ClassFile, FieldOrMethodInfo, SignatureParser }
import sbt.internal.inc.classfile.SignatureModel._
import sbt.util.Logger

/**
 * Builds an [[xsbti.api.ClassLike]] for a Java class directly from its classfile, without loading
 * it via reflection. This is the fallback used for classes that cannot be reflectively loaded
 * during post-compile analysis (sbt/zinc#837, sbt/sbt#117) — without it those classes get no API
 * recorded, so name-hashing can miss dependents when the class's own public shape changes.
 *
 * It mirrors the structure [[ClassToAPI]] produces (a class + module `ClassLike` pair, declared
 * members split into static/instance) and reuses [[ClassToAPI]]'s access/modifier/type helpers, so
 * the result slots into the same analysis. Member types use real generics parsed from the `Signature`
 * attribute ([[SignatureParser]]), falling back to erased JVM descriptors when it is absent; class
 * and method type parameters and enum children are modelled. Checked exceptions (`throws`) and
 * declared annotations (type + element values) are folded into a synthetic annotation so changes are
 * detected. Inherited members are not modelled (only declared ones) — see Phase 3 in
 * docs/design/classfile-based-java-api.md. The output need not match the reflection-derived API
 * byte-for-byte (e.g. type-variable names are unqualified, and wildcards keep only their bound); it
 * only needs to be deterministic and to change when the class's public shape changes.
 */
object ClassfileToAPI {
  import api.DefinitionType.{ ClassDef, Module, Trait }

  private val noAnnotations = new Array[api.Annotation](0)
  private val noTypeParameters = new Array[api.TypeParameter](0)
  private val noTypes = new Array[api.Type](0)
  private val noStrings = new Array[String](0)
  private val noDefinitions = new Array[api.ClassDefinition](0)
  private val AccEnum = 0x4000 // ACC_ENUM; not exposed by java.lang.reflect.Modifier
  private val AccVarargs = 0x0080 // ACC_VARARGS (on a method)

  private def strict[T <: AnyRef](t: T): api.Lazy[T] = SafeLazyProxy.strict(t)

  // A synthetic annotation that folds public-shape signals not carried by the modelled member types —
  // checked-exception (throws) types and declared annotations (type + element values), plus the raw
  // Signature string as a parse-failure fallback — into the API, so changing any of them changes the
  // hash.
  private val SyntheticRef = ClassToAPI.reference("xsbti.api.ClassfileApi")

  private def syntheticAnnotations(parts: (String, Iterable[String])*): Array[api.Annotation] = {
    val args = parts.flatMap {
      case (label, values) =>
        if (values.isEmpty) None
        else Some(api.AnnotationArgument.of(label, values.mkString(",")))
    }
    if (args.isEmpty) noAnnotations
    else Array(api.Annotation.of(SyntheticRef, args.toArray))
  }

  /** Resolved checked-exception type names from a method's `Exceptions` attribute (JVMS 4.7.5). */
  private def exceptionNames(cf: ClassFile, attrs: Seq[AttributeInfo]): Seq[String] =
    attrs.find(_.isNamed("Exceptions")) match {
      case None => Nil
      case Some(a) =>
        try {
          val in = new DataInputStream(new ByteArrayInputStream(a.value))
          val count = in.readUnsignedShort()
          List.fill(count) {
            val classConstant = cf.constantPool(in.readUnsignedShort())
            cf.constantPool(classConstant.nameIndex).value.fold("")(_.toString).replace('/', '.')
          }
        } catch { case NonFatal(_) => Nil }
    }

  /**
   * Declared annotations rendered as `type(name=value,...)` strings, from RuntimeVisible/Invisible
   * attributes (JVMS 4.7.16) — capturing element values too, so `@A("a")` differs from `@A("b")`.
   */
  private def annotationStrings(cf: ClassFile, attrs: Seq[AttributeInfo]): Seq[String] = {
    val out = ArrayBuffer.empty[String]
    for (a <- attrs if a.isRuntimeVisibleAnnotations || a.isRuntimeInvisibleAnnotations) {
      try {
        val in = new DataInputStream(new ByteArrayInputStream(a.value))
        def poolStr(i: Int): String = cf.constantPool(i).value.fold("")(_.toString)
        // Length-prefix each composed value so concatenation can't collide (e.g. {"a,b","c"} vs
        // {"a","b,c"}); we only need distinct, deterministic strings for hashing, not parseability.
        def lp(s: String): String = s.length.toString + ":" + s
        def elementValue(): String =
          in.readUnsignedByte().toChar match {
            case 'e' => poolStr(in.readUnsignedShort()) + "." + poolStr(in.readUnsignedShort())
            case 'c' => poolStr(in.readUnsignedShort())
            case '@' => annotationString()
            case '[' =>
              val n = in.readUnsignedShort()
              (0 until n).map(_ => lp(elementValue())).mkString("[", "", "]")
            case _ => poolStr(in.readUnsignedShort())
          }
        def annotationString(): String = {
          val tpe =
            poolStr(in.readUnsignedShort()).stripPrefix("L").stripSuffix(";").replace('/', '.')
          val pairs = in.readUnsignedShort()
          val args =
            (0 until pairs).map(_ => lp(poolStr(in.readUnsignedShort()) + "=" + elementValue()))
          if (args.isEmpty) tpe else args.mkString(tpe + "(", "", ")")
        }
        val num = in.readUnsignedShort()
        (0 until num).foreach(_ => out += annotationString())
      } catch { case NonFatal(_) => () }
    }
    out.toSeq
  }

  /**
   * Produces the API (class + module `ClassLike` for each input) and the names of any classes that
   * declare a `main` method. Each input pairs the class's source (canonical) name — supplied by the
   * caller so it stays consistent with the product/dependency relations — with its parsed classfile.
   */
  def process(
      named: Seq[(String, ClassFile)],
      log: Logger = Logger.Null
  ): (Seq[api.ClassLike], Seq[String]) = {
    val classApis = ArrayBuffer.empty[api.ClassLike]
    val mainClasses = ArrayBuffer.empty[String]
    for ((name, cf) <- named) {
      classApis ++= classLikes(name, cf)
      if (cf.methods.exists(_.isMain)) mainClasses += name
    }
    (classApis.toSeq, mainClasses.toSeq)
  }

  private def classLikes(name: String, cf: ClassFile): Seq[api.ClassLike] = {
    // Use the binary name's package (last '.' before the simple/binary name); the canonical name's
    // dots would mis-split nested classes (e.g. "pkg.Outer.Inner").
    val enclPkg = ClassToAPI.packageAndName(cf.className)._1
    val mods = ClassToAPI.modifiers(cf.accessFlags)
    val acc = ClassToAPI.access(cf.accessFlags, enclPkg)
    val isInterface = Modifier.isInterface(cf.accessFlags)
    val tpe = if (isInterface) Trait else ClassDef
    // Top-level unless the classfile's InnerClasses attribute lists itself as a member of another.
    val topLevel =
      !cf.innerClasses.exists(i => i.innerClassName == cf.className && i.outerClassName.nonEmpty)

    val fields = cf.fields.toIndexedSeq.map(fieldDef(cf, _, enclPkg))
    val methods = cf.methods.toIndexedSeq.collect {
      case m if !m.name.contains("<clinit>") => methodDef(cf, m, cf.className, enclPkg)
    }
    val (staticFields, instanceFields) = fields.partition(_._1)
    val (staticMethods, instanceMethods) = methods.partition(_._1)
    val instanceDeclared: Array[api.ClassDefinition] =
      (instanceFields.map(_._2) ++ instanceMethods.map(_._2)).toArray
    val staticDeclared: Array[api.ClassDefinition] =
      (staticFields.map(_._2) ++ staticMethods.map(_._2)).toArray

    val classSigStr = cf.attributes.find(_.isSignature).map(cf.stringValue)
    val classSig = classSigStr.flatMap(SignatureParser.classSignature)
    val (typeParams, parents): (Array[api.TypeParameter], Array[api.Type]) = classSig match {
      case Some(cs) =>
        (
          cs.typeParams.map(toTypeParam).toArray,
          (cs.superclass +: cs.interfaces).map(toType).toArray
        )
      case None =>
        val erased = (cf.superClassName +: cf.interfaceNames.toIndexedSeq)
          .filter(_.nonEmpty)
          .map(ClassToAPI.reference)
          .toArray
        (noTypeParameters, erased)
    }

    val classAnnots = syntheticAnnotations(
      "signature" -> unparsedSignature(classSigStr, classSig),
      "annotations" -> annotationStrings(cf, cf.attributes.toIndexedSeq)
    )

    // ClassToAPI models a Java enum's sealed children as a single self-reference; mirror that.
    val children: Array[api.Type] =
      if ((cf.accessFlags & AccEnum) != 0) Array(ClassToAPI.reference(name)) else noTypes

    val instanceStructure =
      api.Structure.of(strict(parents), strict(instanceDeclared), strict(noDefinitions))
    val staticStructure =
      api.Structure.of(strict(noTypes), strict(staticDeclared), strict(noDefinitions))

    val cls = api.ClassLike.of(
      name,
      acc,
      mods,
      classAnnots,
      tpe,
      strict(ClassToAPI.Empty),
      strict(instanceStructure),
      noStrings,
      children,
      topLevel,
      typeParams
    )
    val stat = api.ClassLike.of(
      name,
      acc,
      mods,
      classAnnots,
      Module,
      strict(ClassToAPI.Empty),
      strict(staticStructure),
      noStrings,
      noTypes,
      topLevel,
      noTypeParameters
    )
    cls :: stat :: Nil
  }

  /** (isStatic, FieldLike) for a classfile field. */
  private def fieldDef(
      cf: ClassFile,
      f: FieldOrMethodInfo,
      enclPkg: Option[String]
  ): (Boolean, api.ClassDefinition) = {
    val name = f.name.getOrElse("")
    val mods = ClassToAPI.modifiers(f.accessFlags)
    // Compilers inline static-final constants, so fold the constant value into the type (mirroring
    // ClassToAPI.singletonForConstantField) so a value change (X = 1 -> 2) changes the hash.
    val constant: Option[AnyRef] =
      if (mods.isFinal)
        (try cf.constantValue(name)
        catch { case NonFatal(_) => None })
      else None
    val sigStr = f.attributes.find(_.isSignature).map(cf.stringValue)
    val sig = sigStr.flatMap(SignatureParser.fieldSignature)
    val tpe = constant match {
      case Some(value) =>
        val tag = name + "$" + f.descriptor.getOrElse("") + "$" + value
        api.Singleton.of(ClassToAPI.pathFromStrings(cf.className.split("\\.").toIndexedSeq :+ tag))
      case None => sig.map(toType).getOrElse(parseFieldType(f.descriptor.getOrElse("V")))
    }
    val acc = ClassToAPI.access(f.accessFlags, enclPkg)
    val annots = syntheticAnnotations(
      "signature" -> unparsedSignature(sigStr, sig),
      "annotations" -> annotationStrings(cf, f.attributes)
    )
    val fieldLike =
      if (mods.isFinal) api.Val.of(name, acc, mods, annots, tpe)
      else api.Var.of(name, acc, mods, annots, tpe)
    (f.isStatic, fieldLike)
  }

  /** (isStatic, Def) for a classfile method; `<init>` is named like [[ClassToAPI]]'s constructors. */
  private def methodDef(
      cf: ClassFile,
      m: FieldOrMethodInfo,
      binaryName: String,
      enclPkg: Option[String]
  ): (Boolean, api.ClassDefinition) = {
    val sigStr = m.attributes.find(_.isSignature).map(cf.stringValue)
    val sig = sigStr.flatMap(SignatureParser.methodSignature)
    val (paramTypes, rawReturn, typeParams): (Array[api.Type], api.Type, Array[api.TypeParameter]) =
      sig match {
        case Some(ms) =>
          (ms.params.map(toType).toArray, toType(ms.result), ms.typeParams.map(toTypeParam).toArray)
        case None =>
          val (ps, ret) = parseMethodType(m.descriptor.getOrElse("()V"))
          (ps, ret, noTypeParameters)
      }
    val isCtor = m.name.contains("<init>")
    // ClassToAPI uses Empty (not Unit) for a constructor's "return type".
    val returnType = if (isCtor) ClassToAPI.Empty else rawReturn
    // ACC_VARARGS marks the trailing array parameter as repeated (`T...` vs `T[]`).
    val isVarargs = (m.accessFlags & AccVarargs) != 0
    val lastParam = paramTypes.length - 1
    val params = paramTypes.zipWithIndex.map {
      case (t, i) =>
        val modifier =
          if (isVarargs && i == lastParam) api.ParameterModifier.Repeated
          else api.ParameterModifier.Plain
        api.MethodParameter.of("", t, false, modifier)
    }
    val paramList = api.ParameterList.of(params, false)
    // Match ClassToAPI.uniqueConstructorName, which uses the binary (not canonical) class name.
    val name =
      if (isCtor) s"${binaryName.replace('.', ';')};init;"
      else m.name.getOrElse("")
    val acc = ClassToAPI.access(m.accessFlags, enclPkg)
    val mods = ClassToAPI.modifiers(m.accessFlags)
    val annots = syntheticAnnotations(
      "signature" -> unparsedSignature(sigStr, sig),
      "throws" -> exceptionNames(cf, m.attributes),
      "annotations" -> annotationStrings(cf, m.attributes)
    )
    val d = api.Def.of(name, acc, mods, annots, typeParams, Array(paramList), returnType)
    (m.isStatic, d)
  }

  private val ObjectRef = ClassToAPI.reference("java.lang.Object")

  // --- Generic signatures (JVMS 4.7.9) parsed by SignatureParser, mapped to api types ----------

  private def toType(t: SigType): api.Type =
    t match {
      case SigPrimitive(tag) => ClassToAPI.primitive(primitiveName(tag))
      case SigVoid           => ClassToAPI.primitive("void")
      case SigArray(elem)    => ClassToAPI.array(toType(elem))
      case SigVar(varName)   => api.ParameterRef.of(varName)
      case SigClass(clsName, args, inners) =>
        val full = (clsName +: inners.map(_.name)).mkString(".")
        val all = (args ++ inners.flatMap(_.args)).map(toTypeArg).toArray
        if (all.isEmpty) ClassToAPI.reference(full)
        else api.Parameterized.of(ClassToAPI.reference(full), all)
    }

  // A wildcard's bound is kept (so `List<? extends Number>` differs from `List<? extends String>`);
  // the extends/super direction is not separately modelled. Unbounded `*` -> `_`, as ClassToAPI does.
  private def toTypeArg(a: SigArg): api.Type =
    a match {
      case SigWildcard        => ClassToAPI.reference("_")
      case SigBounded(_, tpe) => toType(tpe)
    }

  private def toTypeParam(p: SigTypeParam): api.TypeParameter =
    api.TypeParameter.of(
      p.name,
      noAnnotations,
      noTypeParameters,
      api.Variance.Invariant,
      ClassToAPI.NothingRef,
      api.Structure.of(
        strict(p.bounds.map(toType).toArray),
        strict(noDefinitions),
        strict(noDefinitions)
      )
    )

  private def primitiveName(tag: Char): String =
    tag match {
      case 'B' => "byte"
      case 'C' => "char"
      case 'D' => "double"
      case 'F' => "float"
      case 'I' => "int"
      case 'J' => "long"
      case 'S' => "short"
      case 'Z' => "boolean"
      case _   => "void"
    }

  // The raw signature is folded into the synthetic annotation only as a fallback — when a Signature
  // attribute is present but SignatureParser could not parse it — so generic changes are still
  // detected. When it parses, the real mapped types above carry the generics instead.
  private def unparsedSignature(raw: Option[String], parsed: Option[Any]): Iterable[String] =
    if (raw.isDefined && parsed.isEmpty) raw.toList else Nil

  /** Parses a JVM field descriptor (JVMS 4.3.2) into an erased [[xsbti.api.Type]]. */
  private[inc] def parseFieldType(descriptor: String): api.Type = parseType(descriptor, 0)._1

  /** Parses a JVM method descriptor (JVMS 4.3.3) into (parameter types, return type), erased. */
  private[inc] def parseMethodType(descriptor: String): (Array[api.Type], api.Type) = {
    val params = ArrayBuffer.empty[api.Type]
    var i = 1 // skip '('
    while (i < descriptor.length && descriptor.charAt(i) != ')') {
      val (tpe, next) = parseType(descriptor, i)
      params += tpe
      i = next
    }
    // `i` should sit on ')'; on a malformed/truncated descriptor fall back to a void return rather
    // than indexing past the end.
    val returnType =
      if (i + 1 < descriptor.length) parseType(descriptor, i + 1)._1
      else ClassToAPI.primitive("void")
    (params.toArray, returnType)
  }

  /**
   * Parses one field type starting at `i`, returning the type and the index just past it. Unknown
   * or truncated tokens (corrupt classfile, or a future descriptor form) degrade to an opaque
   * `Object` reference rather than throwing — this is a best-effort fallback that must never fail a
   * compile that would otherwise succeed.
   */
  private def parseType(descriptor: String, i: Int): (api.Type, Int) =
    if (i >= descriptor.length) (ObjectRef, i + 1)
    else
      descriptor.charAt(i) match {
        case 'B' => (ClassToAPI.primitive("byte"), i + 1)
        case 'C' => (ClassToAPI.primitive("char"), i + 1)
        case 'D' => (ClassToAPI.primitive("double"), i + 1)
        case 'F' => (ClassToAPI.primitive("float"), i + 1)
        case 'I' => (ClassToAPI.primitive("int"), i + 1)
        case 'J' => (ClassToAPI.primitive("long"), i + 1)
        case 'S' => (ClassToAPI.primitive("short"), i + 1)
        case 'Z' => (ClassToAPI.primitive("boolean"), i + 1)
        case 'V' => (ClassToAPI.primitive("void"), i + 1)
        case '[' =>
          val (elem, next) = parseType(descriptor, i + 1)
          (ClassToAPI.array(elem), next)
        case 'L' =>
          val semi = descriptor.indexOf(';', i)
          val end = if (semi < 0) descriptor.length else semi
          (ClassToAPI.reference(descriptor.substring(i + 1, end).replace('/', '.')), end + 1)
        case _ => (ObjectRef, i + 1)
      }
}
