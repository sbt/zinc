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

import java.lang.reflect.{ Array => _, _ }
import java.lang.annotation.Annotation
import annotation.tailrec
import inc.classfile.{ ClassFile, FieldOrMethodInfo, ParsedAnnotation, Constants => CF }
import xsbti.api
import xsbti.api.SafeLazyProxy
import collection.mutable
import sbt.io.IO
import sbt.util.Logger

object ClassToAPI {
  def apply(c: Seq[Class[?]]): Seq[api.ClassLike] = process(c)._1

  // (api, public inherited classes)
  def process(
      classes: Seq[Class[?]],
      log: Logger = Logger.Null
  ): (Seq[api.ClassLike], Seq[String], Set[(Class[?], Class[?])]) = {
    val cmap = emptyClassMap(log)
    classes.foreach(toDefinitions(cmap)) // force recording of class definitions
    cmap.lz.toList
      .foreach(_.get()) // force thunks to ensure all inherited dependencies are recorded
    val classApis = cmap.allNonLocalClasses.toSeq
    val mainClasses = cmap.mainClasses.toSeq
    val inDeps = cmap.inherited.toSet
    cmap.clear()
    (classApis, mainClasses, inDeps)
  }

  // Avoiding implicit allocation.
  private def arrayMap[T <: AnyRef, U <: AnyRef: reflect.ClassTag](
      xs: Array[T]
  )(f: T => U): Array[U] = {
    val len = xs.length
    var i = 0
    val res = new Array[U](len)
    while (i < len) {
      res(i) = f(xs(i))
      i += 1
    }
    res
  }

  def packages(c: Seq[Class[?]]): Set[String] =
    c.flatMap(packageName).toSet

  def isTopLevel(c: Class[?]): Boolean =
    c.getEnclosingClass eq null

  final class ClassMap private[sbt] (
      private[sbt] val memo: mutable.Map[String, Seq[api.ClassLikeDef]],
      private[sbt] val inherited: mutable.Set[(Class[?], Class[?])],
      private[sbt] val lz: mutable.Buffer[xsbti.api.Lazy[?]],
      private[sbt] val allNonLocalClasses: mutable.Set[api.ClassLike],
      private[sbt] val mainClasses: mutable.Set[String],
      private[sbt] val log: Logger
  ) {
    def clear(): Unit = {
      memo.clear()
      inherited.clear()
      lz.clear()
    }
  }
  def emptyClassMap(log: Logger = Logger.Null): ClassMap =
    new ClassMap(
      new mutable.HashMap,
      new mutable.HashSet,
      new mutable.ListBuffer,
      new mutable.HashSet,
      new mutable.HashSet,
      log
    )

  /**
   * Returns the canonical name given a class based on https://docs.oracle.com/javase/specs/jls/se11/html/jls-6.html#jls-6.7
   *
   * 1. A named package returns its package name.
   * 2A. A top-level class returns package name + "." + simple name.
   * 2B. A top-level Scala object returns object's name + "$".
   * 3A. Nested class M of a class C returns C's canonical name + "." + M's simple name.
   * 3B. Nested class M of a top-level Scala object O returns O's name + "." + M's simple name.
   * 3C. Nested class M of a non-top-level Scala object O returns's O's canonical name + "." + M's simple name.
   *
   * For example OOO (object in object in object) returns `p1.O1.O2$.O3$`.
   * @return The canonical name if not null, the blank string otherwise.
   */
  def classCanonicalName(c: Class[?]): String = {
    def handleMalformedNameOf(c: Class[?]): String = {
      if (c == null) "" // Return nothing if it hits the top-level class
      else {
        val className = c.getName
        try {
          val canonicalName = c.getCanonicalName
          if (canonicalName == null) className
          else canonicalName
        } catch {
          case malformedError: java.lang.InternalError
              if malformedError.getMessage.contains("Malformed class name") =>
            val enclosingClass = c.getEnclosingClass
            val enclosingName = enclosingClass.getName
            val restOfName = c.getName.stripPrefix(enclosingName)
            // https://docs.oracle.com/javase/specs/jls/se11/html/jls-6.html#jls-6.7
            // A member class or member interface M declared in another class or interface C has a canonical name if and only if C has a canonical name.
            // In that case, the canonical name of M consists of the canonical name of C, followed by ".", followed by the simple name of M.
            handleMalformedNameOf(enclosingClass) + "." + restOfName
        }
      }
    }
    handleMalformedNameOf(c)
  }

  def toDefinitions(cmap: ClassMap)(c: Class[?]): Seq[api.ClassLikeDef] =
    cmap.memo.getOrElseUpdate(classCanonicalName(c), toDefinitions0(c, cmap))

  def toDefinitions0(c: Class[?], cmap: ClassMap): Seq[api.ClassLikeDef] = {
    import api.DefinitionType.{ ClassDef, Module, Trait }
    val enclPkg = packageName(c)
    val mods = modifiers(c.getModifiers)
    val acc = access(c.getModifiers, enclPkg)
    val annots = classAnnotationsSafe(c, cmap)
    val children = childrenOfSealedClass(c)
    val topLevel = c.getEnclosingClass == null
    val name = classCanonicalName(c)
    val tpe = if (Modifier.isInterface(c.getModifiers)) Trait else ClassDef
    val tparams = classTypeParametersSafe(c, cmap)
    lazy val (static, instance) = structure(c, enclPkg, cmap)
    val cls = api.ClassLike.of(
      name,
      acc,
      mods,
      annots,
      tpe,
      lzyS(Empty),
      lzy(instance, cmap),
      emptyStringArray,
      children.toArray,
      topLevel,
      tparams
    )
    val clsDef =
      api.ClassLikeDef.of(name, acc, mods, annots, tparams, tpe)
    val stat = api.ClassLike.of(
      name,
      acc,
      mods,
      annots,
      Module,
      lzyS(Empty),
      lzy(static, cmap),
      emptyStringArray,
      emptyTypeArray,
      topLevel,
      emptyTypeParameterArray
    )
    val statDef = api.ClassLikeDef.of(name, acc, mods, annots, emptyTypeParameterArray, Module)
    val defs = cls :: stat :: Nil
    val defsEmptyMembers = clsDef :: statDef :: Nil
    cmap.memo(name) = defsEmptyMembers
    cmap.allNonLocalClasses ++= defs

    if (classFileForClass(c).methods.exists(_.isMain)) {
      cmap.mainClasses += name
    }

    defsEmptyMembers
  }

  /**
   * Returns the (static structure, instance structure) for `c`.
   *
   * Reflection is the primary path. If the JVM can't resolve a referenced type
   * (typically a transitive dependency marked optional/provided that isn't on
   * the analysis classpath), we fall back to parsing the classfile directly.
   * The classfile fallback produces lower-fidelity output (no generic type
   * parameters; see DescriptorParser for the erased-type substitution).
   */
  def structure(
      c: Class[?],
      enclPkg: Option[String],
      cmap: ClassMap
  ): (api.Structure, api.Structure) =
    try structureReflective(c, enclPkg, cmap)
    catch {
      case e: (LinkageError | TypeNotPresentException | ClassNotFoundException) =>
        cmap.log.warn(
          s"Reflection failed introspecting ${c.getName} " +
            s"(${e.getClass.getSimpleName}: ${e.getMessage}); falling back to classfile parser"
        )
        structureFromClassfile(c, enclPkg, cmap)
    }

  private def structureReflective(
      c: Class[?],
      enclPkg: Option[String],
      cmap: ClassMap
  ): (api.Structure, api.Structure) = {
    lazy val cf = classFileForClass(c)
    val methods = mergeMap(
      c,
      c.getDeclaredMethods.toIndexedSeq,
      c.getMethods.toIndexedSeq,
      methodToDef(enclPkg)
    )
    val fields = mergeMap(
      c,
      c.getDeclaredFields.toIndexedSeq,
      c.getFields.toIndexedSeq,
      fieldToDef(c, cf, enclPkg)
    )
    val constructors =
      mergeMap(
        c,
        c.getDeclaredConstructors.toIndexedSeq,
        c.getConstructors.toIndexedSeq,
        constructorToDef(enclPkg)
      )
    val classes = innerClassesFromClassfile(c, cf, cmap)
    val all = methods ++ fields ++ constructors ++ classes
    val parentJavaTypes = allSuperTypes(c)
    if (!Modifier.isPrivate(c.getModifiers))
      cmap.inherited ++= parentJavaTypes.collect { case parent: Class[?] => c -> parent }
    val parentTypes = types(parentJavaTypes)
    val instanceStructure =
      api.Structure.of(lzyS(parentTypes), lzyS(all.declared.toArray), lzyS(all.inherited.toArray))
    val staticStructure = api.Structure.of(
      lzyEmptyTpeArray,
      lzyS(all.staticDeclared.toArray),
      lzyS(all.staticInherited.toArray)
    )
    (staticStructure, instanceStructure)
  }

  private def structureFromClassfile(
      c: Class[?],
      enclPkg: Option[String],
      cmap: ClassMap
  ): (api.Structure, api.Structure) = {
    val cf = classFileForClass(c)

    val declaredMethods = cf.methods.filter(m =>
      !m.isConstructor && !m.isStaticInit && !m.isBridge && !m.isSynthetic
    )
    val declaredConstructors = cf.methods.filter(m => m.isConstructor && !m.isSynthetic)
    val declaredFields = cf.fields

    val inheritedMethods = cfPublicInherited(
      c,
      _.methods.filter(m =>
        m.isPublic && !m.isConstructor && !m.isStaticInit && !m.isBridge && !m.isSynthetic
      )
    )
    val inheritedFields = cfPublicInherited(c, _.fields.filter(_.isPublic))

    val methods = cfMerge(
      c,
      cf,
      declaredMethods,
      inheritedMethods,
      cfMethodToDef(enclPkg)
    )
    val fields = cfMerge(
      c,
      cf,
      declaredFields,
      inheritedFields,
      cfFieldToDef(enclPkg)
    )
    // Constructors are never inherited.
    val constructors = cfMerge(
      c,
      cf,
      declaredConstructors,
      Seq.empty,
      cfConstructorToDef(enclPkg)
    )
    val classes = innerClassesFromClassfile(c, cf, cmap)
    val all = methods ++ fields ++ constructors ++ classes

    val parentJavaTypes = allSuperTypesSafe(c)
    if (!Modifier.isPrivate(c.getModifiers))
      cmap.inherited ++= parentJavaTypes.collect { case parent: Class[?] => c -> parent }
    val parentTypes = types(parentJavaTypes)
    val instanceStructure =
      api.Structure.of(lzyS(parentTypes), lzyS(all.declared.toArray), lzyS(all.inherited.toArray))
    val staticStructure = api.Structure.of(
      lzyEmptyTpeArray,
      lzyS(all.staticDeclared.toArray),
      lzyS(all.staticInherited.toArray)
    )
    (staticStructure, instanceStructure)
  }

  /**
   * Best-effort supertype walk for the classfile fallback: tolerates the same
   * reflection failures that triggered the fallback. We use the classfile's
   * superClassName / interfaceNames when reflection on the parents throws.
   */
  private def allSuperTypesSafe(c: Class[?]): Seq[Type] =
    try allSuperTypes(c)
    catch {
      case _: (LinkageError | TypeNotPresentException | ClassNotFoundException) => Seq.empty
    }

  /**
   * Reflection on `c.getAnnotations` triggers loading of every annotation type, which
   * fails when an annotation lives in an optional/provided dep that isn't on the analysis
   * classpath. Fall back to parsing the classfile's RuntimeVisible/Invisible Annotations
   * attributes when reflection blows up.
   */
  private def classAnnotationsSafe(c: Class[?], cmap: ClassMap): Array[api.Annotation] =
    try annotations(c.getAnnotations)
    catch {
      case e: (LinkageError | TypeNotPresentException | ClassNotFoundException) =>
        cmap.log.warn(
          s"Reflection failed reading annotations on ${c.getName} " +
            s"(${e.getClass.getSimpleName}: ${e.getMessage}); falling back to classfile parser"
        )
        try {
          val cf = classFileForClass(c)
          cfAnnotations(cf.annotations(cf.attributes.toIndexedSeq))
        } catch { case _: Throwable => emptyAnnotationArray }
    }

  /**
   * `c.getTypeParameters` triggers resolution of generic bound types. Fall back to an
   * empty array when that fails — the Signature attribute parser (deferred) would let us
   * recover the type parameters from the classfile bytes.
   */
  private def classTypeParametersSafe(c: Class[?], cmap: ClassMap): Array[api.TypeParameter] =
    try typeParameters(typeParameterTypes(c))
    catch {
      case e: (LinkageError | TypeNotPresentException | ClassNotFoundException) =>
        cmap.log.warn(
          s"Reflection failed reading type parameters on ${c.getName} " +
            s"(${e.getClass.getSimpleName}: ${e.getMessage}); using empty type parameter list"
        )
        emptyTypeParameterArray
    }

  /**
   * Returns members from supertypes paired with the supertype's `(Class, ClassFile)` so
   * downstream code can read each member's attributes against the correct constant pool
   * (attribute bytes contain indices that only make sense within the originating classfile).
   */
  private def cfPublicInherited(
      c: Class[?],
      select: ClassFile => Array[FieldOrMethodInfo]
  ): Seq[(Class[?], ClassFile, FieldOrMethodInfo)] =
    allSuperTypesSafe(c).collect { case parent: Class[?] =>
      val pcf = classFileForClass(parent)
      select(pcf).iterator.map(m => (parent, pcf, m)).toSeq
    }.flatten

  private def cfMerge(
      declaringClass: Class[?],
      declaringCf: ClassFile,
      declared: Array[FieldOrMethodInfo],
      inherited: Seq[(Class[?], ClassFile, FieldOrMethodInfo)],
      toDef: (Class[?], ClassFile, FieldOrMethodInfo) => api.ClassDefinition
  ): Defs = {
    val (selfStatic, selfInstance) = declared.partition(_.isStatic)
    val (inhStatic, inhInstance) = inherited.partition(_._3.isStatic)
    Defs(
      selfInstance.iterator.map(m => toDef(declaringClass, declaringCf, m)).toSeq,
      inhInstance.iterator.map { case (pc, pcf, m) => toDef(pc, pcf, m) }.toSeq,
      selfStatic.iterator.map(m => toDef(declaringClass, declaringCf, m)).toSeq,
      inhStatic.iterator.map { case (pc, pcf, m) => toDef(pc, pcf, m) }.toSeq
    )
  }

  private def cfAccess(flags: Int, pkg: Option[String]): api.Access = {
    if ((flags & CF.ACC_PUBLIC) != 0) Public
    else if ((flags & CF.ACC_PRIVATE) != 0) Private
    else if ((flags & CF.ACC_PROTECTED) != 0) Protected
    else packagePrivate(pkg)
  }

  private def cfModifiers(flags: Int): api.Modifiers =
    new api.Modifiers(
      (flags & CF.ACC_ABSTRACT) != 0,
      false,
      (flags & CF.ACC_FINAL) != 0,
      false,
      false,
      false,
      false,
      false
    )

  private def cfMethodToDef(
      enclPkg: Option[String]
  )(declaringClass: Class[?], cf: ClassFile, m: FieldOrMethodInfo): api.ClassDefinition = {
    val _ = declaringClass // unused for methods, but kept for uniform toDef signature
    val mName = m.name.getOrElse("")
    val (paramTypes, retType) = m.descriptor
      .map(DescriptorParser.methodTypes)
      .getOrElse((Array.empty[api.Type], Empty))
    val params = cfParameterList(m, paramTypes, cf.parameterAnnotations(m.attributes))
    val annots =
      cfAnnotations(cf.annotations(m.attributes)) ++
        cfExceptionAnnotations(cf.methodExceptions(m.attributes))
    api.Def.of(
      mName,
      cfAccess(m.accessFlags, enclPkg),
      cfModifiers(m.accessFlags),
      annots,
      emptyTypeParameterArray,
      Array(params),
      retType
    )
  }

  private def cfFieldToDef(
      enclPkg: Option[String]
  )(declaringClass: Class[?], declaringCf: ClassFile, f: FieldOrMethodInfo): api.ClassDefinition = {
    val fName = f.name.getOrElse("")
    val fType = f.descriptor.map(DescriptorParser.fieldType).getOrElse(Empty)
    val mods = cfModifiers(f.accessFlags)
    val accs = cfAccess(f.accessFlags, enclPkg)
    val annots = cfAnnotations(declaringCf.annotations(f.attributes))
    val specificTpe: Option[api.Type] =
      if (mods.isFinal)
        declaringCf.constantValue(fName).map { v =>
          api.Singleton.of(
            pathFromStrings(
              declaringClass.getName.split("\\.").toSeq :+
                (fName + "$" + f.descriptor.getOrElse("") + "$" + v)
            )
          )
        }
      else None
    val tpe = specificTpe.getOrElse(fType)
    if (mods.isFinal) api.Val.of(fName, accs, mods, annots, tpe)
    else api.Var.of(fName, accs, mods, annots, tpe)
  }

  private def cfConstructorToDef(
      enclPkg: Option[String]
  )(declaringClass: Class[?], cf: ClassFile, m: FieldOrMethodInfo): api.ClassDefinition = {
    val cName = s"${declaringClass.getName.replace('.', ';')};init;"
    val (paramTypes, _) = m.descriptor
      .map(DescriptorParser.methodTypes)
      .getOrElse((Array.empty[api.Type], Empty))
    val params = cfParameterList(m, paramTypes, cf.parameterAnnotations(m.attributes))
    val annots =
      cfAnnotations(cf.annotations(m.attributes)) ++
        cfExceptionAnnotations(cf.methodExceptions(m.attributes))
    api.Def.of(
      cName,
      cfAccess(m.accessFlags, enclPkg),
      cfModifiers(m.accessFlags),
      annots,
      emptyTypeParameterArray,
      Array(params),
      Empty
    )
  }

  private def cfParameterList(
      m: FieldOrMethodInfo,
      paramTypes: Array[api.Type],
      paramAnnots: IndexedSeq[IndexedSeq[ParsedAnnotation]]
  ): api.ParameterList = {
    val lastIdx = paramTypes.length - 1
    val params = paramTypes.zipWithIndex.map { case (tpe, i) =>
      val modifier =
        if (m.isVarArgs && i == lastIdx) api.ParameterModifier.Repeated
        else api.ParameterModifier.Plain
      val annots = if (i < paramAnnots.length) paramAnnots(i) else IndexedSeq.empty
      val annotated =
        if (annots.isEmpty) tpe
        else api.Annotated.of(tpe, cfAnnotations(annots))
      api.MethodParameter.of("", annotated, false, modifier)
    }
    api.ParameterList.of(params, false)
  }

  private def cfAnnotations(
      parsed: IndexedSeq[ParsedAnnotation]
  ): Array[api.Annotation] =
    if (parsed.isEmpty) emptyAnnotationArray
    else parsed.iterator.map(cfAnnotation).toArray

  private def cfAnnotation(a: ParsedAnnotation): api.Annotation = {
    val args =
      if (a.arguments.isEmpty) Array.empty[api.AnnotationArgument]
      else a.arguments.iterator.map(p => api.AnnotationArgument.of(p.name, p.value)).toArray
    api.Annotation.of(DescriptorParser.fieldType(a.typeDescriptor), args)
  }

  private def cfExceptionAnnotations(exceptions: IndexedSeq[String]): Array[api.Annotation] =
    if (exceptions.isEmpty) emptyAnnotationArray
    else
      exceptions.iterator.map { name =>
        api.Annotation.of(
          Throws,
          Array(api.AnnotationArgument.of("value", s"class $name"))
        )
      }.toArray

  /** Enumerates inner classes from the classfile instead of reflection. sbt/sbt#117 */
  private def innerClassesFromClassfile(
      c: Class[?],
      cf: => ClassFile,
      cmap: ClassMap
  ): Defs = {
    val cl = c.getClassLoader
    val name = c.getName
    val declaredClasses = new mutable.ArrayBuffer[Class[?]]()
    val inheritedClasses = new mutable.ArrayBuffer[Class[?]]()
    // direct inner classes from this class's classfile
    for (info <- cf.innerClasses if info.outerClassName == name) {
      loadInnerClass(cl, info, cmap.log).foreach(declaredClasses += _)
    }
    // inherited public inner classes from parent classfiles
    for {
      parent <- allSuperTypes(c).collect { case c: Class[?] => c }
      parentCf = classFileForClass(parent)
      info <- parentCf.innerClasses if info.outerClassName == parent.getName && info.isPublic
    } {
      loadInnerClass(cl, info, cmap.log).foreach(inheritedClasses += _)
    }
    merge[Class[?]](
      c,
      declaredClasses.toIndexedSeq,
      (declaredClasses.filter(cls => Modifier.isPublic(cls.getModifiers)) ++ inheritedClasses).toIndexedSeq,
      toDefinitions(cmap),
      (_: Seq[Class[?]]).partition(isStatic),
      _.getEnclosingClass != c
    )
  }

  private def loadInnerClass(
      cl: ClassLoader,
      info: classfile.InnerClassInfo,
      log: Logger
  ): Option[Class[?]] = {
    // Bootstrap-loaded classes (e.g. java.lang.Thread.Builder on JDK 21+)
    // report a null ClassLoader; fall back to the system class loader.
    val loader = if (cl == null) ClassLoader.getSystemClassLoader else cl
    try Some(loader.loadClass(info.innerClassName))
    catch {
      case e: (ClassNotFoundException | NoClassDefFoundError) =>
        log.warn(s"Could not load inner class ${info.innerClassName}: $e")
        None
    }
  }

  private def classFileForClass(c: Class[?]): ClassFile =
    classfile.Parser.apply(IO.classfileLocation(c), Logger.Null)

  @inline private def lzyS[T <: AnyRef](t: T): xsbti.api.Lazy[T] = SafeLazyProxy.strict(t)
  @inline final def lzy[T <: AnyRef](t: => T): xsbti.api.Lazy[T] = SafeLazyProxy(t)
  private def lzy[T <: AnyRef](t: => T, cmap: ClassMap): xsbti.api.Lazy[T] = {
    val s = lzy(t)
    cmap.lz += s
    s
  }

  private val emptyStringArray = new Array[String](0)
  private val emptyTypeArray = new Array[xsbti.api.Type](0)
  private val emptyAnnotationArray = new Array[xsbti.api.Annotation](0)
  private val emptyTypeParameterArray = new Array[xsbti.api.TypeParameter](0)
  private val lzyEmptyTpeArray = lzyS(emptyTypeArray)
  private val lzyEmptyDefArray = lzyS(new Array[xsbti.api.ClassDefinition](0))

  private def allSuperTypes(t: Type): Seq[Type] = {
    @tailrec def accumulate(t: Type, accum: Seq[Type] = Seq.empty): Seq[Type] = t match {
      case c: Class[?] =>
        val (parent, interfaces) = (c.getGenericSuperclass, c.getGenericInterfaces.toIndexedSeq)
        accumulate(parent, (accum :+ parent) ++ flattenAll(interfaces))
      case p: ParameterizedType =>
        accumulate(p.getRawType, accum)
      case _ =>
        accum
    }
    @tailrec def flattenAll(interfaces: Seq[Type], accum: Seq[Type] = Seq.empty): Seq[Type] = {
      if (interfaces.nonEmpty) {
        val raw = interfaces map { case p: ParameterizedType => p.getRawType; case i => i }
        val children = raw flatMap {
          case i: Class[?] => i.getGenericInterfaces; case _ => Seq.empty
        }
        flattenAll(children, accum ++ interfaces ++ children)
      } else
        accum
    }
    accumulate(t).filterNot(_ == null).distinct
  }

  def types(ts: Seq[Type]): Array[api.Type] =
    ts.filter(_ ne null).map(reference).toArray
  def upperBounds(ts: Array[Type]): api.Type =
    api.Structure.of(lzy(types(ts.toIndexedSeq)), lzyEmptyDefArray, lzyEmptyDefArray)

  @deprecated("No longer used", "0.13.0")
  def parents(c: Class[?]): Seq[api.Type] = types(allSuperTypes(c))

  @deprecated("Use fieldToDef[4] instead", "0.13.9")
  def fieldToDef(enclPkg: Option[String])(f: Field): api.FieldLike = {
    val c = f.getDeclaringClass
    fieldToDef(c, classFileForClass(c), enclPkg)(f)
  }

  def fieldToDef(c: Class[?], cf: => ClassFile, enclPkg: Option[String])(
      f: Field
  ): api.FieldLike = {
    val name = f.getName
    val accs = access(f.getModifiers, enclPkg)
    val mods = modifiers(f.getModifiers)
    val annots = annotations(f.getDeclaredAnnotations)
    val fieldTpe = reference(returnType(f))
    // generate a more specific type for constant fields
    val specificTpe: Option[api.Type] =
      if (mods.isFinal) {
        try {
          cf.constantValue(name).map(singletonForConstantField(c, f, _))
        } catch {
          case e: Throwable =>
            throw new IllegalStateException(
              s"Failed to parse class $c: this may mean your classfiles are corrupted. Please clean and try again.",
              e
            )
        }
      } else {
        None
      }
    val tpe = specificTpe.getOrElse(fieldTpe)
    if (mods.isFinal) {
      api.Val.of(name, accs, mods, annots, tpe)
    } else {
      api.Var.of(name, accs, mods, annots, tpe)
    }
  }

  /**
   * Creates a Singleton type that includes both the type and ConstantValue for the given Field.
   *
   * Since java compilers are allowed to inline constant (static final primitive) fields in
   * downstream classfiles, we generate a type that will cause APIs to match only when both
   * the type and value of the field match. We include the classname mostly for readability.
   *
   * Because this type is purely synthetic, it's fine that the name might contain filename-
   * banned characters.
   */
  private def singletonForConstantField(c: Class[?], field: Field, constantValue: AnyRef) =
    api.Singleton.of(
      pathFromStrings(
        c.getName
          .split("\\.")
          .toSeq :+ (field.getName + "$" + returnType(field) + "$" + constantValue)
      )
    )

  def methodToDef(enclPkg: Option[String])(m: Method): api.Def =
    defLike(
      m.getName,
      m.getModifiers,
      m.getDeclaredAnnotations,
      typeParameterTypes(m),
      m.getParameterAnnotations,
      parameterTypes(m),
      Option(returnType(m)),
      exceptionTypes(m),
      m.isVarArgs,
      enclPkg
    )

  /** Use the unique constructor format defined in [[xsbt.ClassName.constructorName]]. */
  private def uniqueConstructorName(constructor: Constructor[?]): String =
    s"${name(constructor).replace('.', ';')};init;"
  def constructorToDef(enclPkg: Option[String])(c: Constructor[?]): api.Def =
    defLike(
      uniqueConstructorName(c),
      c.getModifiers,
      c.getDeclaredAnnotations,
      typeParameterTypes(c),
      c.getParameterAnnotations,
      parameterTypes(c),
      None,
      exceptionTypes(c),
      c.isVarArgs,
      enclPkg
    )

  def defLike[T <: GenericDeclaration](
      name: String,
      mods: Int,
      annots: Array[Annotation],
      tps: Array[TypeVariable[T]],
      paramAnnots: Array[Array[Annotation]],
      paramTypes: Array[Type],
      retType: Option[Type],
      exceptions: Array[Type],
      varArgs: Boolean,
      enclPkg: Option[String]
  ): api.Def = {
    val varArgPosition = if (varArgs) paramTypes.length - 1 else -1
    val isVarArg = List.tabulate(paramTypes.length)(_ == varArgPosition)
    val pa = paramAnnots.lazyZip(paramTypes).lazyZip(isVarArg).map {
      case (a, p, v) => parameter(a, p, v)
    }
    val params = api.ParameterList.of(pa, false)
    val ret = retType match { case Some(rt) => reference(rt); case None => Empty }
    api.Def.of(
      name,
      access(mods, enclPkg),
      modifiers(mods),
      annotations(annots) ++ exceptionAnnotations(exceptions),
      typeParameters(tps),
      Array(params),
      ret
    )
  }

  def exceptionAnnotations(exceptions: Array[Type]): Array[api.Annotation] =
    if (exceptions.length == 0) emptyAnnotationArray
    else
      arrayMap(exceptions)(t =>
        api.Annotation.of(Throws, Array(api.AnnotationArgument.of("value", t.toString)))
      )

  def parameter(annots: Array[Annotation], parameter: Type, varArgs: Boolean): api.MethodParameter =
    api.MethodParameter.of(
      "",
      annotated(reference(parameter), annots),
      false,
      if (varArgs) api.ParameterModifier.Repeated else api.ParameterModifier.Plain
    )

  def annotated(t: api.Type, annots: Array[Annotation]): api.Type =
    (
      if (annots.length == 0) t
      else api.Annotated.of(t, annotations(annots))
    )

  case class Defs(
      declared: Seq[api.ClassDefinition],
      inherited: Seq[api.ClassDefinition],
      staticDeclared: Seq[api.ClassDefinition],
      staticInherited: Seq[api.ClassDefinition]
  ) {
    def ++(o: Defs) =
      Defs(
        declared ++ o.declared,
        inherited ++ o.inherited,
        staticDeclared ++ o.staticDeclared,
        staticInherited ++ o.staticInherited
      )
  }
  def mergeMap[T <: Member](
      of: Class[?],
      self: Seq[T],
      public: Seq[T],
      f: T => api.ClassDefinition
  ): Defs =
    merge[T](of, self, public, x => f(x) :: Nil, splitStatic, _.getDeclaringClass != of)

  def merge[T](
      of: Class[?],
      self: Seq[T],
      public: Seq[T],
      f: T => Seq[api.ClassDefinition],
      splitStatic: Seq[T] => (Seq[T], Seq[T]),
      isInherited: T => Boolean
  ): Defs = {
    val (selfStatic, selfInstance) = splitStatic(self)
    val (inheritedStatic, inheritedInstance) = splitStatic(public filter isInherited)
    Defs(
      selfInstance flatMap f,
      inheritedInstance flatMap f,
      selfStatic flatMap f,
      inheritedStatic flatMap f
    )
  }

  def splitStatic[T <: Member](defs: Seq[T]): (Seq[T], Seq[T]) =
    defs partition isStatic

  def isStatic(c: Class[?]): Boolean = Modifier.isStatic(c.getModifiers)
  def isStatic(a: Member): Boolean = Modifier.isStatic(a.getModifiers)

  def typeParameters[T <: GenericDeclaration](
      tps: Array[TypeVariable[T]]
  ): Array[api.TypeParameter] =
    if (tps.length == 0) emptyTypeParameterArray
    else arrayMap(tps)(typeParameter)

  def typeParameter[T <: GenericDeclaration](tp: TypeVariable[T]): api.TypeParameter =
    api.TypeParameter.of(
      typeVariable(tp),
      emptyAnnotationArray,
      emptyTypeParameterArray,
      api.Variance.Invariant,
      NothingRef,
      upperBounds(tp.getBounds)
    )

  // needs to be stable across compilations
  def typeVariable[T <: GenericDeclaration](tv: TypeVariable[T]): String =
    name(tv.getGenericDeclaration) + " " + tv.getName

  def reduceHash(in: Array[Byte]): Int =
    in.foldLeft(0)((acc, b) => (acc * 43) ^ b)

  def name(gd: GenericDeclaration): String =
    gd match {
      case c: Class[?]       => classCanonicalName(c)
      case m: Method         => m.getName
      case c: Constructor[?] => c.getName
    }

  def modifiers(i: Int): api.Modifiers = {
    import Modifier.{ isAbstract, isFinal }
    new api.Modifiers(isAbstract(i), false, isFinal(i), false, false, false, false, false)
  }
  def access(i: Int, pkg: Option[String]): api.Access = {
    import Modifier.{ isPublic, isPrivate, isProtected }
    if (isPublic(i)) Public
    else if (isPrivate(i)) Private
    else if (isProtected(i)) Protected
    else packagePrivate(pkg)
  }

  def annotations(a: Array[Annotation]): Array[api.Annotation] =
    if (a.length == 0) emptyAnnotationArray else arrayMap(a)(annotation)
  def annotation(a: Annotation): api.Annotation =
    api.Annotation.of(reference(a.annotationType), Array(javaAnnotation(a.toString)))

  /**
   * This method mimics Scala compiler's behavior of `Symbol.children` method when Symbol corresponds to
   * a Java-defined enum class. Java's enum is modelled as a sealed class and enum's constants are modelled as
   * children.
   *
   * We need this logic to trigger recompilation due to changes to pattern exhaustivity checking results.
   */
  private def childrenOfSealedClass(c: Class[?]): Seq[api.Type] =
    if (!c.isEnum) emptyTypeArray.toIndexedSeq
    else {
      // Calling getCanonicalName() on classes from enum constants yields same string as enumClazz.getCanonicalName
      // Moreover old behaviour create new instance of enum - what may fail (e.g. in static block )
      Seq(reference(c))
    }

  // full information not available from reflection
  def javaAnnotation(s: String): api.AnnotationArgument =
    api.AnnotationArgument.of("toString", s)

  def array(tpe: api.Type): api.Type = api.Parameterized.of(ArrayRef, Array(tpe))
  def reference(c: Class[?]): api.Type =
    if (c.isArray) array(reference(c.getComponentType))
    else if (c.isPrimitive) primitive(c.getName)
    else reference(classCanonicalName(c))

  // does not handle primitives
  def reference(s: String): api.Type = {
    val (pkg, cls) = packageAndName(s)
    pkg match {
      // translate all primitives?
      case None => api.Projection.of(Empty, cls)
      case Some(p) =>
        api.Projection.of(api.Singleton.of(pathFromString(p)), cls)
    }
  }

  // sbt/zinc#389: Ignore nulls coming from generic parameter types of lambdas
  private def ignoreNulls[T](genericTypes: Array[T]): Array[T] =
    genericTypes.filter(_ != null)

  def referenceP(t: ParameterizedType): api.Parameterized = {
    val targs = ignoreNulls(t.getActualTypeArguments)
    val args = if (targs.isEmpty) emptyTypeArray else arrayMap(targs)(t => reference(t): api.Type)
    val base = reference(t.getRawType)
    api.Parameterized.of(base, args)
  }

  def reference(t: Type): api.Type =
    t match {
      case _: WildcardType       => reference("_")
      case tv: TypeVariable[?]   => api.ParameterRef.of(typeVariable(tv))
      case pt: ParameterizedType => referenceP(pt)
      case gat: GenericArrayType => array(reference(gat.getGenericComponentType))
      case c: Class[?]           => reference(c)
    }

  def pathFromString(s: String): api.Path =
    pathFromStrings(s.split("\\.").toIndexedSeq)
  def pathFromStrings(ss: Seq[String]): api.Path =
    api.Path.of((ss.map(api.Id.of(_)) :+ ThisRef).toArray)
  def packageName(c: Class[?]) = packageAndName(c)._1
  def packageAndName(c: Class[?]): (Option[String], String) =
    packageAndName(c.getName)
  def packageAndName(name: String): (Option[String], String) = {
    val lastDot = name.lastIndexOf('.')
    if (lastDot >= 0)
      (Some(name.substring(0, lastDot)), name.substring(lastDot + 1))
    else
      (None, name)
  }

  val Empty = api.EmptyType.of()
  val ThisRef = api.This.of()

  val Public = api.Public.of()
  val Unqualified = api.Unqualified.of()
  val Private = api.Private.of(Unqualified)
  val Protected = api.Protected.of(Unqualified)
  def packagePrivate(pkg: Option[String]): api.Access =
    api.Private.of(api.IdQualifier.of(pkg getOrElse ""))

  val ArrayRef = reference("scala.Array")
  val Throws = reference("scala.throws")
  val NothingRef = reference("scala.Nothing")

  private def PrimitiveNames =
    Seq("boolean", "byte", "char", "short", "int", "long", "float", "double")
  private def PrimitiveMap = PrimitiveNames.map(j => (j, j.capitalize)) :+ ("void" -> "Unit")
  private val PrimitiveRefs = PrimitiveMap.map {
    case (n, sn) => (n, reference("scala." + sn))
  }.toMap
  def primitive(name: String): api.Type = PrimitiveRefs(name)

  private def returnType(f: Field): Type = f.getGenericType
  private def returnType(m: Method): Type = m.getGenericReturnType
  private def exceptionTypes(c: Constructor[?]): Array[Type] = c.getGenericExceptionTypes

  private def exceptionTypes(m: Method): Array[Type] = m.getGenericExceptionTypes

  private def parameterTypes(m: Method): Array[Type] =
    ignoreNulls(m.getGenericParameterTypes)

  private def parameterTypes(c: Constructor[?]): Array[Type] =
    ignoreNulls(c.getGenericParameterTypes)

  private def typeParameterTypes[T](m: Constructor[T]): Array[TypeVariable[Constructor[T]]] =
    m.getTypeParameters
  private def typeParameterTypes[T](m: Class[T]): Array[TypeVariable[Class[T]]] =
    m.getTypeParameters
  private def typeParameterTypes(m: Method): Array[TypeVariable[Method]] =
    m.getTypeParameters
}
