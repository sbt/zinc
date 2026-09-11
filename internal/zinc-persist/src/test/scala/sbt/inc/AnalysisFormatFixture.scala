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

package sbt.internal.inc

import java.nio.file.Paths

import scala.reflect.ClassTag

import sbt.internal.inc.Analysis.{ LocalProduct, NonLocalProduct }
import sbt.internal.inc.APIs.emptyModifiers
// explicit: xsbti.compile._ below also defines a UsedName
import sbt.internal.inc.{ UsedName, UsedNames }
import sbt.util.InterfaceUtil
import sbt.util.InterfaceUtil.t2
import xsbti.{ Severity, UseScope, VirtualFileRef }
import xsbti.api._
import xsbti.api.DependencyContext._
import xsbti.compile._
import xsbti.compile.analysis.SourceInfo

/**
 * A fixed, exhaustive Analysis used to pin the serialized shape of the analysis formats. Every
 * value is spelled out rather than generated, so the rendering is stable across runs and cannot
 * drift when the ScalaCheck generators change. Lives in `sbt.internal.inc` to reach `addUsedNames`,
 * following the same convention as [[AnalysisGenerators]].
 *
 * Two properties matter and are easy to break when editing this file:
 *   - every scalar written adjacently by a writer is distinct, so transposing two fields changes
 *     the rendering (identical neighbours would make the swap invisible);
 *   - collections that drive a writer body are non-empty, so that body is actually rendered.
 */
object AnalysisFormatFixture {
  private def lzy[A <: AnyRef](a: A) = SafeLazyProxy.strict(a)
  private def arr[A <: AnyRef: ClassTag] = new Array[A](0)
  private def vf(id: String) = VirtualFileRef.of(id)

  private def tparam(id: String) = TypeParameter.of(
    id,
    arr[Annotation],
    arr[TypeParameter],
    Variance.Invariant,
    EmptyType.of(),
    EmptyType.of()
  )

  private lazy val annotation =
    Annotation.of(
      ParameterRef.of("Ann"),
      Array(AnnotationArgument.of("argName", "argValue"), AnnotationArgument.of("emptyArg", ""))
    )

  private lazy val simplePath = Path.of(Array(Id.of("pkg"), This.of()))
  private lazy val superPath = Path.of(Array(Super.of(Path.of(Array(This.of()))), Id.of("outer")))
  // Last component is not `This`, so this takes writePath's non-simple arm with a `This` in it.
  private lazy val thisPath = Path.of(Array(This.of(), Id.of("member")))

  /** One value per branch of `writeClassDefinition`, which also covers every `Access`. */
  private lazy val definitions: Array[ClassDefinition] = Array(
    ClassLikeDef.of(
      "classLikeDef",
      Public.of(),
      emptyModifiers,
      arr[Annotation],
      Array(tparam("CT")),
      DefinitionType.ClassDef
    ),
    Val.of(
      "valDef",
      Protected.of(IdQualifier.of("protectedQualifier")),
      emptyModifiers,
      arr[Annotation],
      ParameterRef.of("VT")
    ),
    Var.of(
      "varDef",
      Private.of(ThisQualifier.of()),
      emptyModifiers,
      arr[Annotation],
      ParameterRef.of("WT")
    ),
    Def.of(
      "defDef",
      Private.of(Unqualified.of()),
      emptyModifiers,
      Array(annotation),
      Array(tparam("DT")),
      Array(
        ParameterList.of(
          Array(MethodParameter.of("param", ParameterRef.of("PT"), true, ParameterModifier.Plain)),
          false
        )
      ),
      EmptyType.of()
    ),
    TypeAlias.of(
      "typeAlias",
      Public.of(),
      emptyModifiers,
      arr[Annotation],
      Array(tparam("AT")),
      EmptyType.of()
    ),
    TypeDeclaration.of(
      "typeDeclaration",
      Public.of(),
      emptyModifiers,
      arr[Annotation],
      Array(tparam("ZT")),
      EmptyType.of(),
      ParameterRef.of("UT")
    )
  )

  /** One value per branch of `writeType`. */
  private lazy val types: Array[Type] = Array(
    ParameterRef.of("paramRef"),
    Parameterized.of(ParameterRef.of("List"), Array[Type](ParameterRef.of("Elem"))),
    Structure.of(lzy(Array[Type](EmptyType.of())), lzy(definitions), lzy(arr[ClassDefinition])),
    Polymorphic.of(EmptyType.of(), Array(tparam("PolyT"))),
    Constant.of(ParameterRef.of("Int"), "constantValue"),
    Existential.of(EmptyType.of(), Array(tparam("ExT"))),
    Singleton.of(simplePath),
    Projection.of(Singleton.of(superPath), "projected"),
    Annotated.of(ParameterRef.of("annotatedBase"), Array(annotation)),
    EmptyType.of()
  )

  private lazy val richStructure =
    Structure.of(lzy(types), lzy(definitions), lzy(arr[ClassDefinition]))

  private lazy val emptyStructure =
    Structure.of(lzy(arr[Type]), lzy(arr[ClassDefinition]), lzy(arr[ClassDefinition]))

  /**
   * `rich` fills the four array slots of `writeClassLike`, which are otherwise never rendered.
   * `childrenOfSealedClass` repeats a Path already written inside the structure, which is what
   * reaches `Serializer.dedup`'s back-reference arm.
   */
  private def classLike(
      name: String,
      defnType: DefinitionType,
      structure: Structure,
      rich: Boolean
  ) =
    ClassLike.of(
      name,
      Public.of(),
      emptyModifiers,
      if (rich) Array(annotation) else arr[Annotation],
      defnType,
      lzy[Type](EmptyType.of()),
      lzy(structure),
      if (rich) Array("savedAnnotation") else arr[String],
      if (rich) Array[Type](Singleton.of(thisPath), Singleton.of(simplePath)) else arr[Type],
      true,
      if (rich) Array(tparam("ClassT")) else arr[TypeParameter]
    )

  // Only one ClassLike carries the exhaustive type and definition coverage; repeating it in every
  // companion would quadruple the golden file without reaching any new branch.
  private def analyzedClass(name: String, structure: Structure) = AnalyzedClass.of(
    11L,
    name,
    lzy(
      Companions.of(
        classLike(name, DefinitionType.ClassDef, structure, rich = structure eq richStructure),
        classLike(name, DefinitionType.Module, emptyStructure, rich = false)
      )
    ),
    12,
    Array(NameHash.of("nameHash", UseScope.Default, 13)),
    true,
    14,
    "provenance",
    // The 8-arg overload defaults both of these to 0, which would make transposing the two
    // adjacent `out.long` writes in writeAnalyzedClass invisible.
    15L,
    16L
  )

  private def position(sourcePath: String, sourceFile: String) = InterfaceUtil.position(
    line0 = Some(21),
    content = "final class A",
    offset0 = Some(22),
    pointer0 = Some(23),
    pointerSpace0 = Some("  "),
    sourcePath0 = Some(sourcePath),
    // Distinct from sourcePath0, and a single segment: anything deeper would serialize with the
    // platform separator.
    sourceFile0 = Some(new java.io.File(sourceFile)),
    startOffset0 = Some(24),
    endOffset0 = Some(25),
    startLine0 = Some(26),
    startColumn0 = Some(27),
    endLine0 = Some(28),
    endColumn0 = Some(29)
  )

  /** Exercises `writeProblem` and the position encoding, both skipped by an empty SourceInfo. */
  private lazy val info: SourceInfo = SourceInfos.makeInfo(
    reported = Seq(
      InterfaceUtil.problem(
        "reportedCategory",
        position("A.scala", "A-reported.scala"),
        "reported message",
        Severity.Warn,
        Some("rendered reported"),
        None,
        Nil,
        Nil
      )
    ),
    unreported = Seq(
      InterfaceUtil.problem(
        "unreportedCategory",
        position("A.scala", "A-unreported.scala"),
        "unreported message",
        Severity.Error,
        None,
        None,
        Nil,
        Nil
      )
    ),
    mainClasses = Seq("MainClass")
  )

  private lazy val usedNames = UsedNames.fromMultiMap(
    Map(
      "A" -> Set(
        // Two names in one scope group, so the sort inside writeUsedNameSet actually reorders.
        UsedName("usedByDefault", List(UseScope.Default)),
        UsedName("alsoUsedByDefault", List(UseScope.Default)),
        UsedName("usedImplicitly", List(UseScope.Implicit, UseScope.PatMatTarget))
      )
    )
  )

  /** Each `writeStamp2` branch and each internal `DependencyContext` appears exactly once. */
  lazy val analysis: Analysis = {
    val base = Analysis.empty.addSource(
      src = vf("A.scala"),
      apis = Seq(analyzedClass("A", richStructure)),
      stamp = Hash.unsafeFromString("cafebabe"),
      info = info,
      // binaryClassName differs from className so the productClassName relation is not A -> A,
      // where transposing key and value would be invisible.
      nonLocalProducts =
        NonLocalProduct("A", "ABinary", vf("A.class"), FarmHash.fromLong(31L)) :: Nil,
      localProducts = LocalProduct(vf("A$1.class"), new LastModified(32L)) :: Nil,
      internalDeps = List(
        InternalDependency.of("A", "MemberRefTarget", DependencyByMemberRef),
        InternalDependency.of("A", "InheritanceTarget", DependencyByInheritance),
        InternalDependency.of("A", "LocalInheritanceTarget", LocalDependencyByInheritance),
        InternalDependency.of("A", "MacroTarget", DependencyByMacroExpansion)
      ),
      externalDeps = List(
        ExternalDependency.of("A", "C", analyzedClass("C", emptyStructure), DependencyByMemberRef),
        ExternalDependency
          .of(
            "A",
            "ExtInherit",
            analyzedClass("ExtInherit", emptyStructure),
            DependencyByInheritance
          ),
        ExternalDependency.of(
          "A",
          "ExtLocalInherit",
          analyzedClass("ExtLocalInherit", emptyStructure),
          LocalDependencyByInheritance
        ),
        ExternalDependency
          .of(
            "A",
            "ExtMacro",
            analyzedClass("ExtMacro", emptyStructure),
            DependencyByMacroExpansion
          )
      ),
      libraryDeps = (vf("x.jar"), "x", EmptyStamp) :: Nil
    )
    base.copy(relations = base.relations.addUsedNames(usedNames))
  }

  lazy val setup: MiniSetup = MiniSetup.of(
    // A single path segment: a deeper path would serialize with the platform separator.
    CompileOutput(Paths.get("out")),
    MiniOptions.of(
      Array(FileHash.of(Paths.get("lib.jar"), 41)),
      Array("-Xfatal-warnings", "-deprecation"),
      Array("-source", "8")
    ),
    "3.9.0",
    CompileOrder.Mixed,
    true,
    Array(t2("extraKey" -> "extraValue"))
  )
}
