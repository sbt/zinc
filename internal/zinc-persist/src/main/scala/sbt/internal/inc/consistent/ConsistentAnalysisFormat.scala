package sbt.internal.inc.consistent

import java.nio.file.Paths
import java.util.{ Arrays, Comparator, EnumSet }
import sbt.internal.inc.{ UsedName, Stamp => StampImpl, _ }
import sbt.internal.util.Relation
import sbt.util.InterfaceUtil
import xsbti.{ Problem, Severity, UseScope, VirtualFileRef }
import xsbti.api._
import xsbti.compile._
import xsbti.compile.analysis.{ ReadWriteMappers, SourceInfo, Stamp }

import scala.collection.immutable.TreeMap
import sbt.internal.inc.binary.converters.InternalApiProxy
import Compat._

/** A new implementation of zinc's incremental state serialization.
 * - Full structural serialization (like the existing protobuf format), no shortcuts with sbinary
 *   or Java serialization (like the existing text format).
 * - A single implementation that supports an efficient binary format for production use and a
 *   text format for development and debugging.
 * - Consistent output files: If two compiler runs result in the same internal representation of
 *   incremental state (after applying WriteMappers), they produce identical zinc files.
 * - Smaller output files than the existing binary format.
 * - Faster serialization and deserialization than the existing binary format.
 * - Smaller implementation than either of the existing formats.
 */
class ConsistentAnalysisFormat(val mappers: ReadWriteMappers, sort: Boolean) {
  import ConsistentAnalysisFormat._

  private[this] final val VERSION = 1100029
  private[this] final val readMapper = mappers.getReadMapper
  private[this] final val writeMapper = mappers.getWriteMapper

  def write(out: Serializer, analysis: CompileAnalysis, setup: MiniSetup): Unit = {
    val analysis0 = analysis match { case analysis: Analysis => analysis }
    out.int(VERSION)
    writeMiniSetup(out, setup)
    writeRelations(out, analysis0.relations)
    writeStamps(out, analysis0.stamps)
    writeAPIs(out, analysis0.apis, setup.storeApis())
    writeSourceInfos(out, analysis0.infos)
    // we do not read or write the Compilations
    out.int(VERSION)
    out.end()
  }

  def read(in: Deserializer): (CompileAnalysis, MiniSetup) = {
    readVersion(in)
    val setup = readMiniSetup(in)
    val relations = readRelations(in)
    val stamps = readStamps(in)
    val apis = readAPIs(in, setup.storeApis())
    val infos = readSourceInfos(in)
    // we do not read or write the Compilations
    val compilations = Compilations.of(Nil)
    readVersion(in)
    in.end()
    (Analysis.Empty.copy(stamps, apis, relations, infos, compilations), setup)
  }

  @inline
  private[this] final def writeMaybeSortedStringMap[V](
      out: Serializer,
      name: String,
      map: scala.collection.Iterable[(String, V)],
      perEntry: Int = 1
  )(f: V => Unit): Unit =
    if (sort) out.writeSortedStringMap(name, map, perEntry)(f)
    else out.writeColl(name, map, perEntry + 1) { kv => out.string(kv._1); f(kv._2) }

  private[this] def readVersion(in: Deserializer): Unit = {
    val ver = in.int()
    if (ver != VERSION) throw new Exception(s"Unsupported format version $ver")
  }

  private[this] def writeStamp2(out: Serializer, stamp: Stamp): Unit = stamp match {
    case hash: FarmHash     => out.byte(0); out.long(hash.hashValue)
    case hash: Hash         => out.byte(1); out.string(hash.hexHash)
    case hash: LastModified => out.byte(2); out.long(hash.value)
    case _                  => out.byte(3); out.string(stamp.toString)
  }

  private[this] def readStamp2(in: Deserializer): Stamp = in.byte() match {
    case 0 => FarmHash.fromLong(in.long())
    case 1 => Hash.unsafeFromString(in.string())
    case 2 => new LastModified(in.long())
    case 3 => StampImpl.fromString(in.string())
  }

  private[this] def writeStamps(out: Serializer, stamps: Stamps): Unit = {
    writeMaybeSortedStringMap(
      out,
      "stamps.products",
      stamps.products.view.map { case (f, stamp) =>
        (writeMapper.mapProductFile(f).id, writeMapper.mapProductStamp(f, stamp))
      },
      2
    )(writeStamp2(out, _))
    writeMaybeSortedStringMap(
      out,
      "stamps.sources",
      stamps.sources.view.map { case (f, stamp) =>
        (writeMapper.mapSourceFile(f).id, writeMapper.mapSourceStamp(f, stamp))
      },
      2
    )(writeStamp2(out, _))
    writeMaybeSortedStringMap(
      out,
      "stamps.libraries",
      stamps.libraries.view.map { case (f, stamp) =>
        (writeMapper.mapBinaryFile(f).id, writeMapper.mapBinaryStamp(f, stamp))
      },
      2
    )(writeStamp2(out, _))
  }

  private[this] def readStamps(in: Deserializer): Stamps = {
    import VirtualFileUtil._
    val products =
      in.readColl[(VirtualFileRef, Stamp), TreeMap[VirtualFileRef, Stamp]](TreeMap, 3) {
        val f = readMapper.mapProductFile(VirtualFileRef.of(in.string()))
        (f, readMapper.mapProductStamp(f, readStamp2(in)))
      }
    val sources = in.readColl[(VirtualFileRef, Stamp), TreeMap[VirtualFileRef, Stamp]](TreeMap, 3) {
      val f = readMapper.mapSourceFile(VirtualFileRef.of(in.string()))
      (f, readMapper.mapSourceStamp(f, readStamp2(in)))
    }
    val libraries =
      in.readColl[(VirtualFileRef, Stamp), TreeMap[VirtualFileRef, Stamp]](TreeMap, 3) {
        val f = readMapper.mapBinaryFile(VirtualFileRef.of(in.string()))
        (f, readMapper.mapBinaryStamp(f, readStamp2(in)))
      }
    Stamps(products, sources, libraries)
  }

  private[this] def writeAnalyzedClass(
      out: Serializer,
      ac: AnalyzedClass,
      storeApis: Boolean
  ): Unit = {
    out.writeBlock("analyzedClass") {
      out.string(ac.name())
      out.long(ac.compilationTimestamp())
      out.int(ac.apiHash())
      out.bool(ac.hasMacro)
      out.string(ac.provenance())
      out.int(ac.extraHash())
      out.long(ac.bytecodeHash())
      out.long(ac.transitiveBytecodeHash())
      val nh0 = ac.nameHashes()
      val nh = if (nh0.length > 1 && sort) {
        val nh = nh0.clone()
        Arrays.sort(nh, nameHashComparator)
        nh
      } else nh0
      out.writeArray("nameHashes.name", nh) { h => out.string(h.name()) }
      out.writeArray("nameHashes.scope", nh) { h => out.byte(h.scope().ordinal().toByte) }
      out.writeArray("nameHashes.hash", nh) { h => out.int(h.hash()) }
      if (storeApis) {
        val comp = ac.api()
        writeClassLike(out, comp.classApi())
        writeClassLike(out, comp.objectApi())
      }
    }
  }

  private[this] def readAnalyzedClass(in: Deserializer, storeApis: Boolean): AnalyzedClass = {
    in.readBlock {
      val name = in.string()
      val ts = in.long()
      val ah = in.int()
      val hm = in.bool()
      val p = in.string()
      val eh = in.int()
      val bh = in.long()
      val ebh = in.long()
      val nhNames = in.readStringArray()
      val nhScopes = in.readArray[UseScope]() { UseScope.values()(in.byte().toInt) }
      val nhHashes = in.readArray[Int]() { in.int() }
      val nameHashes = new Array[NameHash](nhNames.length)
      var i = 0
      while (i < nameHashes.length) {
        nameHashes(i) = NameHash.of(nhNames(i), nhScopes(i), nhHashes(i))
        i += 1
      }
      val comp =
        if (storeApis) Companions.of(readClassLike(in), readClassLike(in))
        else APIs.emptyCompanions
      AnalyzedClass.of(ts, name, SafeLazyProxy.strict(comp), ah, nameHashes, hm, eh, p, bh, ebh)
    }
  }

  private[this] def writeAPIs(out: Serializer, apis: APIs, storeApis: Boolean): Unit = {
    def write(n: String, m: Map[String, AnalyzedClass]): Unit =
      writeMaybeSortedStringMap(
        out,
        n,
        m.view.mapValues(_.withCompilationTimestamp(DefaultCompilationTimestamp))
      ) { ac =>
        writeAnalyzedClass(out, ac, storeApis)
      }
    write("internal", apis.internal)
    write("external", apis.external)
  }

  private[this] def readAPIs(in: Deserializer, storeApis: Boolean): APIs = {
    def read() = in.readColl[(String, AnalyzedClass), Map[String, AnalyzedClass]](Map, 2) {
      (in.string(), readAnalyzedClass(in, storeApis))
    }
    APIs(read(), read())
  }

  private[this] def writeSourceInfos(out: Serializer, infos: SourceInfos): Unit = {
    def writeProblem(p: Problem): Unit = out.writeBlock("problem") {
      out.string(p.category())
      out.byte(p.severity().ordinal().toByte)
      out.string(p.message())
      out.writeOptionalString(p.rendered())
      val pos = p.position()
      out.int(pos.line.orElse(-1))
      out.int(pos.offset.orElse(-1))
      out.int(pos.pointer.orElse(-1))
      out.string(pos.lineContent)
      out.string(pos.pointerSpace.orElse(null))
      out.string(pos.sourcePath.orElse(null))
      out.writeOptionalString(pos.sourceFile.map[String](_.toPath.toString))
      out.int(pos.startOffset.orElse(-1))
      out.int(pos.endOffset.orElse(-1))
      out.int(pos.startLine.orElse(-1))
      out.int(pos.startColumn.orElse(-1))
      out.int(pos.endLine.orElse(-1))
      out.int(pos.endColumn.orElse(-1))
    }
    val mapped = infos.allInfos.view.map { case (file, info) =>
      (writeMapper.mapSourceFile(file).id, info)
    }
    writeMaybeSortedStringMap(out, "sourceInfos", mapped, 3) { info =>
      out.writeStringArray("mainClasses", info.getMainClasses)
      out.writeArray("reportedProblems", info.getReportedProblems)(writeProblem)
      out.writeArray("unreportedProblems", info.getUnreportedProblems)(writeProblem)
    }
  }

  private[this] def readSourceInfos(in: Deserializer): SourceInfos = {
    def readProblem(): Problem = in.readBlock {
      val category = in.string()
      val severity = Severity.values.apply(in.byte().toInt)
      val message = in.string()
      val rendered = Option(in.string())
      def io(): Option[Integer] = in.int() match { case -1 => None; case i => Some(i) }
      val line, offset, pointer = io()
      val lineContent = in.string()
      val pointerSpace, sourcePath = Option(in.string())
      val sourceFile = Option(in.string()).map(s => Paths.get(s).toFile)
      val startOffset, endOffset, startLine, startColumn, endLine, endColumn = io()
      val position = InterfaceUtil.position(
        line,
        lineContent,
        offset,
        pointer,
        pointerSpace,
        sourcePath,
        sourceFile,
        startOffset,
        endOffset,
        startLine,
        startColumn,
        endLine,
        endColumn
      )
      InterfaceUtil.problem(category, position, message, severity, rendered, None, Nil, Nil)
    }
    SourceInfos.of(in.readColl[(VirtualFileRef, SourceInfo), Map[VirtualFileRef, SourceInfo]](
      Map,
      4
    ) {
      val file = readMapper.mapSourceFile(VirtualFileRef.of(in.string()))
      val mainClasses = in.readStringSeq()
      val reportedProblems = in.readArray()(readProblem())
      val unreportedProblems = in.readArray()(readProblem())
      val info = SourceInfos.makeInfo(reportedProblems, unreportedProblems, mainClasses)
      (file, info)
    })
  }

  private[this] def writeMiniSetup(out: Serializer, setup0: MiniSetup): Unit = {
    val setup = writeMapper.mapMiniSetup(setup0)
    out.writeBlock("MiniSetup") {
      out.writeArray("classpathHash", setup.options.classpathHash, 2) { fh =>
        out.string(writeMapper.mapClasspathEntry(fh.file).toString)
        out.int(fh.hash())
      }
      out.writeArray("scalacOptions", setup.options.scalacOptions) { s =>
        out.string(writeMapper.mapScalacOption(s))
      }
      out.writeArray("javacOptions", setup.options.javacOptions) { s =>
        out.string(writeMapper.mapJavacOption(s))
      }
      out.string(setup.compilerVersion)
      out.byte(setup.order.ordinal().toByte)
      out.bool(setup.storeApis())
      out.writeArray("extra", setup.extra, 2) { t => out.string(t.get1); out.string(t.get2) }
      val singleOutput = setup.output().getSingleOutputAsPath()
      val outputPath = singleOutput match {
        case o if o.isPresent() && o.get().getFileName().toString().endsWith(".jar") =>
          Analysis.dummyOutputJarPath
        case _ => Analysis.dummyOutputPath
      }
      out.string(outputPath.toString())
    }
  }

  private[this] def readMiniSetup(in: Deserializer): MiniSetup = {
    in.readBlock {
      val classpathHash = in.readArray(2) {
        FileHash.of(readMapper.mapClasspathEntry(Paths.get(in.string())), in.int())
      }
      val scalacOptions = in.readArray() { readMapper.mapScalacOption(in.string()) }
      val javacOptions = in.readArray() { readMapper.mapJavacOption(in.string()) }
      val compilerVersion = in.string()
      val compileOrder = CompileOrder.values()(in.byte().toInt)
      val skipApiStoring = in.bool()
      val extra = in.readArray(2) { InterfaceUtil.t2(in.string() -> in.string()) }
      val outputPath = in.string()
      readMapper.mapMiniSetup(MiniSetup.of(
        CompileOutput(Paths.get(outputPath)),
        MiniOptions.of(classpathHash, scalacOptions, javacOptions),
        compilerVersion,
        compileOrder,
        skipApiStoring,
        extra
      ))
    }
  }

  private[this] def writeRelations(out: Serializer, rs: Relations): Unit = {
    writeMaybeSortedStringMap(out, "usedNames", rs.names.toMultiMap)(writeUsedNameSet(out, _))
    def mapProduct(f: VirtualFileRef) = writeMapper.mapProductFile(f).id
    def mapSource(f: VirtualFileRef) = writeMapper.mapSourceFile(f).id
    def mapBinary(f: VirtualFileRef) = writeMapper.mapBinaryFile(f).id
    def wr[A, B](name: String, rel: Relation[A, B], kf: A => String, vf: B => String): Unit =
      writeMaybeSortedStringMap(
        out,
        name,
        rel.forwardMap.view.map { case (k, vs) => kf(k) -> vs }
      ) { vs =>
        val a = vs.iterator.map(vf).toArray
        if (sort) Arrays.sort(a, implicitly[Ordering[String]])
        out.writeColl("item", a)(out.string)
      }
    def wrS(name: String, rel: Relation[String, String]): Unit =
      wr(name, rel, identity[String], identity[String])
    wr("srcProd", rs.srcProd, mapSource, mapProduct)
    wr("libraryDep", rs.libraryDep, mapSource, mapBinary)
    wr("libraryClassName", rs.libraryClassName, mapBinary, identity[String])
    wr("classes", rs.classes, mapSource, identity[String])
    wrS("memberRef.internal", rs.memberRef.internal)
    wrS("memberRef.external", rs.memberRef.external)
    wrS("inheritance.internal", rs.inheritance.internal)
    wrS("inheritance.external", rs.inheritance.external)
    wrS("localInheritance.internal", rs.localInheritance.internal)
    wrS("localInheritance.external", rs.localInheritance.external)
    wrS("macroExpansion.internal", rs.macroExpansion.internal)
    wrS("macroExpansion.external", rs.macroExpansion.external)
    wrS("productClassNames", rs.productClassName)
  }

  private[this] def readRelations(in: Deserializer): Relations = {
    val un =
      UsedNames.fromMultiMap(in.readColl[(String, Set[UsedName]), Map[String, Set[UsedName]]](
        Map,
        2
      ) {
        (in.string(), readUsedNameSet(in))
      })
    def mapProduct(s: String) = readMapper.mapProductFile(VirtualFileRef.of(s))
    def mapSource(s: String) = readMapper.mapSourceFile(VirtualFileRef.of(s))
    def mapBinary(s: String) = readMapper.mapBinaryFile(VirtualFileRef.of(s))
    def rd[A, B](kf: String => A, vf: String => B): Relation[A, B] =
      Relation.reconstruct(in.readColl[(A, Set[B]), Map[A, Set[B]]](Map, 2) {
        (kf(in.string()), in.readColl[B, Set[B]](Set) { vf(in.string()) })
      })
    def rdS() = rd(identity[String], identity[String])
    val p = rd(mapSource, mapProduct)
    val bin = rd(mapSource, mapBinary)
    val lcn = rd(mapBinary, identity[String])
    val cn = rd(mapSource, identity[String])
    val mri, mre, ii, ie, lii, lie, mei, mee, bcn = rdS()
    def deps(
        m: Relation[String, String],
        i: Relation[String, String],
        l: Relation[String, String],
        me: Relation[String, String],
    ) =
      Map(
        DependencyContext.DependencyByMemberRef -> m,
        DependencyContext.DependencyByInheritance -> i,
        DependencyContext.LocalDependencyByInheritance -> l,
        DependencyContext.DependencyByMacroExpansion -> me,
      )
    Relations.make(
      p,
      bin,
      lcn,
      InternalDependencies(deps(mri, ii, lii, mei)),
      ExternalDependencies(deps(mre, ie, lie, mee)),
      cn,
      un,
      bcn
    )
  }

  private[this] def writeUsedNameSet(out: Serializer, uns: scala.collection.Set[UsedName]): Unit = {
    out.writeBlock("UsedName") {
      val groups0 = uns.iterator.map { un =>
        val sc = un.scopes
        var i = 0
        if (sc.contains(UseScope.Default)) i += 1
        if (sc.contains(UseScope.Implicit)) i += 2
        if (sc.contains(UseScope.PatMatTarget)) i += 4
        (un.name, i.toByte)
      }.toArray.groupBy(_._2)
      val groups = if (sort) groups0.toVector.sortBy(_._1) else groups0
      out.writeColl("groups", groups, 2) { case (g, gNames) =>
        out.byte(g)
        val names = gNames.map(_._1)
        if (sort) Arrays.sort(names, implicitly[Ordering[String]])
        out.writeStringColl("names", names)
      }
    }
  }

  private[this] def readUsedNameSet(in: Deserializer): Set[UsedName] = {
    import scala.jdk.CollectionConverters.*
    in.readBlock {
      val data = in.readColl[Vector[UsedName], Vector[Vector[UsedName]]](Vector, 2) {
        val i = in.byte().toInt
        val names = in.readStringSeq()
        names.iterator.map { n => UsedName(n, useScopes(i).asScala) }.toVector
      }
      data.flatten.toSet
    }
  }

  private[this] def writeClassLike(out: Serializer, cl: ClassLike): Unit =
    out.writeBlock("ClassLike") {
      out.string(cl.name())
      writeAccess(out, cl.access())
      out.byte(cl.modifiers().raw())
      out.writeArray("annotations", cl.annotations())(writeAnnotation(out, _))
      writeDefinitionType(out, cl.definitionType())
      writeType(out, cl.selfType())
      writeStructure(out, cl.structure())
      out.writeStringArray("savedAnnotations", cl.savedAnnotations())
      out.writeArray("childrenOfSealedClass", cl.childrenOfSealedClass())(writeType(out, _))
      out.bool(cl.topLevel())
      out.writeArray("typeParameters", cl.typeParameters())(writeTypeParameter(out, _))
    }

  private[this] def readClassLike(in: Deserializer): ClassLike = in.readBlock {
    val name = in.string()
    val access = readAccess(in)
    val modifiers = InternalApiProxy.Modifiers(in.byte().toInt)
    val annotations = in.readArray[Annotation]()(readAnnotation(in))
    val definitionType = readDefinitionType(in)
    val selfType = SafeLazyProxy.strict(readType(in))
    val structure = SafeLazyProxy.strict(readStructure(in))
    val savedAnnotations = in.readStringArray()
    val childrenOfSealedClass = in.readArray[Type]()(readType(in))
    val topLevel = in.bool()
    val typeParameters = in.readArray[TypeParameter]()(readTypeParameter(in))
    ClassLike.of(
      name,
      access,
      modifiers,
      annotations,
      definitionType,
      selfType,
      structure,
      savedAnnotations,
      childrenOfSealedClass,
      topLevel,
      typeParameters
    )
  }

  private[this] def writeAccess(out: Serializer, access: Access): Unit = out.writeBlock("Access") {
    def writeQualifier(q: Qualifier): Unit = q match {
      case q: IdQualifier   => out.byte(0); out.string(q.value())
      case _: ThisQualifier => out.byte(1)
      case _: Unqualified   => out.byte(2)
    }
    access match {
      case _: Public         => out.byte(0)
      case access: Protected => out.byte(1); writeQualifier(access.qualifier())
      case access: Private   => out.byte(2); writeQualifier(access.qualifier())
    }
  }

  private[this] def readAccess(in: Deserializer): Access = in.readBlock {
    def readQualifier(): Qualifier = in.byte() match {
      case 0 => IdQualifier.of(in.string())
      case 1 => ThisQualifierSingleton
      case 2 => UnqualifiedSingleton
    }
    in.byte() match {
      case 0 => PublicSingleton
      case 1 => Protected.of(readQualifier())
      case 2 => Private.of(readQualifier())
    }
  }

  private[this] def writeAnnotation(out: Serializer, a: Annotation): Unit =
    out.writeBlock("Annotation") {
      writeType(out, a.base())
      out.writeArray("arguments", a.arguments(), 2) { a =>
        out.string(a.name()); out.string(a.value())
      }
    }

  private[this] def readAnnotation(in: Deserializer): Annotation = in.readBlock {
    val base = readType(in)
    val args = in.readArray(2)(AnnotationArgument.of(in.string(), in.string()))
    Annotation.of(base, args)
  }

  private[this] def writeDefinitionType(out: Serializer, dt: DefinitionType): Unit =
    out.byte(dt.ordinal().toByte)

  private[this] def readDefinitionType(in: Deserializer): DefinitionType =
    DefinitionType.values()(in.byte().toInt)

  private[this] def writeTypeParameter(out: Serializer, tp: TypeParameter): Unit =
    out.writeBlock("TypeParameter") {
      out.string(tp.id())
      out.writeArray("annotations", tp.annotations())(writeAnnotation(out, _))
      out.writeArray("typeParameters", tp.typeParameters())(writeTypeParameter(out, _))
      out.byte(tp.variance().ordinal().toByte)
      writeType(out, tp.lowerBound())
      writeType(out, tp.upperBound())
    }

  private[this] def readTypeParameter(in: Deserializer): TypeParameter = in.readBlock {
    TypeParameter.of(
      in.string(),
      in.readArray[Annotation]()(readAnnotation(in)),
      in.readArray[TypeParameter]()(readTypeParameter(in)),
      Variance.values()(in.byte().toInt),
      readType(in),
      readType(in)
    )
  }

  private[this] def writeType(out: Serializer, tpe: Type): Unit = out.writeBlock("Type") {
    tpe match {
      case tpe: ParameterRef =>
        out.byte(0)
        out.string(tpe.id())
      case tpe: Parameterized =>
        out.byte(1)
        writeType(out, tpe.baseType())
        out.writeArray("typeArguments", tpe.typeArguments())(writeType(out, _))
      case tpe: Structure =>
        out.byte(2)
        writeStructure(out, tpe)
      case tpe: Polymorphic =>
        out.byte(3)
        writeType(out, tpe.baseType())
        out.writeArray("parameters", tpe.parameters())(writeTypeParameter(out, _))
      case tpe: Constant =>
        out.byte(4)
        writeType(out, tpe.baseType())
        out.string(tpe.value())
      case tpe: Existential =>
        out.byte(5)
        writeType(out, tpe.baseType())
        out.writeArray("clause", tpe.clause())(writeTypeParameter(out, _))
      case tpe: Singleton =>
        out.byte(6)
        writePath(out, tpe.path())
      case tpe: Projection =>
        out.byte(7)
        writeType(out, tpe.prefix())
        out.string(tpe.id())
      case tpe: Annotated =>
        out.byte(8)
        writeType(out, tpe.baseType())
        out.writeArray("annotations", tpe.annotations())(writeAnnotation(out, _))
      case _: EmptyType => out.byte(9)
    }
  }

  private[this] def readType(in: Deserializer): Type = in.readBlock {
    in.byte() match {
      case 0 => ParameterRef.of(in.string())
      case 1 => Parameterized.of(readType(in), in.readArray[Type]()(readType(in)))
      case 2 => readStructure(in)
      case 3 => Polymorphic.of(readType(in), in.readArray[TypeParameter]()(readTypeParameter(in)))
      case 4 => Constant.of(readType(in), in.string())
      case 5 => Existential.of(readType(in), in.readArray[TypeParameter]()(readTypeParameter(in)))
      case 6 => Singleton.of(readPath(in))
      case 7 => Projection.of(readType(in), in.string())
      case 8 => Annotated.of(readType(in), in.readArray[Annotation]()(readAnnotation(in)))
      case 9 => EmptyTypeSingleton
    }
  }

  private[this] def writeStructure(out: Serializer, tpe: Structure): Unit =
    out.writeBlock("Structure") {
      out.writeArray("parents", tpe.parents())(writeType(out, _))
      out.writeArray("declared", tpe.declared())(writeClassDefinition(out, _))
      out.writeArray("inherited", tpe.inherited())(writeClassDefinition(out, _))
    }

  private[this] def readStructure(in: Deserializer): Structure = in.readBlock {
    val parents = in.readArray[Type]()(readType(in))
    val declared, inherited = in.readArray[ClassDefinition]()(readClassDefinition(in))
    Structure.of(
      SafeLazyProxy.strict(parents),
      SafeLazyProxy.strict(declared),
      SafeLazyProxy.strict(inherited)
    )
  }

  private[this] def writeClassDefinition(out: Serializer, cd: ClassDefinition): Unit =
    out.writeBlock("ClassDefinition") {
      out.string(cd.name())
      writeAccess(out, cd.access())
      out.byte(cd.modifiers().raw())
      out.writeArray("annotations", cd.annotations())(writeAnnotation(out, _))
      cd match {
        case cd: ClassLikeDef =>
          out.byte(0)
          out.writeArray("typeParameters", cd.typeParameters())(writeTypeParameter(out, _))
          writeDefinitionType(out, cd.definitionType())
        case cd: Val =>
          out.byte(1)
          writeType(out, cd.tpe)
        case cd: Var =>
          out.byte(2)
          writeType(out, cd.tpe)
        case cd: Def =>
          out.byte(3)
          out.writeArray("typeParameters", cd.typeParameters())(writeTypeParameter(out, _))
          out.writeArray("valueParameters", cd.valueParameters())(writeParameterList(out, _))
          writeType(out, cd.returnType())
        case cd: TypeAlias =>
          out.byte(4)
          out.writeArray("typeParameters", cd.typeParameters())(writeTypeParameter(out, _))
          writeType(out, cd.tpe())
        case cd: TypeDeclaration =>
          out.byte(5)
          out.writeArray("typeParameters", cd.typeParameters())(writeTypeParameter(out, _))
          writeType(out, cd.lowerBound())
          writeType(out, cd.upperBound())
      }
    }

  private[this] def readClassDefinition(in: Deserializer): ClassDefinition = in.readBlock {
    val name = in.string()
    val access = readAccess(in)
    val modifiers = InternalApiProxy.Modifiers(in.byte().toInt)
    val annotations = in.readArray[Annotation]()(readAnnotation(in))
    in.byte() match {
      case 0 => ClassLikeDef.of(
          name,
          access,
          modifiers,
          annotations,
          in.readArray[TypeParameter]()(readTypeParameter(in)),
          readDefinitionType(in)
        )
      case 1 => Val.of(name, access, modifiers, annotations, readType(in))
      case 2 => Var.of(name, access, modifiers, annotations, readType(in))
      case 3 => Def.of(
          name,
          access,
          modifiers,
          annotations,
          in.readArray[TypeParameter]()(readTypeParameter(in)),
          in.readArray[ParameterList]()(readParameterList(in)),
          readType(in)
        )
      case 4 => TypeAlias.of(
          name,
          access,
          modifiers,
          annotations,
          in.readArray[TypeParameter]()(readTypeParameter(in)),
          readType(in)
        )
      case 5 => TypeDeclaration.of(
          name,
          access,
          modifiers,
          annotations,
          in.readArray[TypeParameter]()(readTypeParameter(in)),
          readType(in),
          readType(in)
        )
    }
  }

  private[this] def writeParameterList(out: Serializer, pl: ParameterList): Unit =
    out.writeBlock("ParameterList") {
      out.writeArray("parameters", pl.parameters(), 4) { mp =>
        out.string(mp.name())
        writeType(out, mp.tpe())
        out.bool(mp.hasDefault)
        out.byte(mp.modifier().ordinal().toByte)
      }
      out.bool(pl.isImplicit)
    }

  private[this] def readParameterList(in: Deserializer): ParameterList = in.readBlock {
    ParameterList.of(
      in.readArray[MethodParameter](4) {
        MethodParameter.of(
          in.string(),
          readType(in),
          in.bool(),
          ParameterModifier.values()(in.byte().toInt)
        )
      },
      in.bool()
    )
  }

  private[this] def isSimplePath(comps: Array[PathComponent]): Boolean = {
    if (comps.isEmpty || !comps.last.isInstanceOf[This]) false
    else {
      var i = 0
      while (i < comps.length - 1) {
        if (!comps(i).isInstanceOf[Id]) return false
        i += 1
      }
      true
    }
  }

  private[this] def writePath(out: Serializer, path: Path): Unit = out.writeBlock("Path") {
    out.dedup(path)(_.components().length) {
      val comps = path.components()
      val simple = isSimplePath(comps)
      if (simple) {
        out.byte(0)
        var i = 0
        while (i < comps.length - 1) {
          out.string(comps(i).asInstanceOf[Id].id)
          i += 1
        }
      } else {
        var i = 0
        while (i < comps.length) {
          comps(i) match {
            case c: Id    => out.byte(1); out.string(c.id)
            case c: Super => out.byte(2); writePath(out, c.qualifier)
            case _: This  => out.byte(3); out.writeBlock("This") {}
          }
          i += 1
        }
      }
    }
  }

  private[this] def readPath(in: Deserializer): Path = {
    in.dedup[Path] { len =>
      val comps = new Array[PathComponent](len)
      val kind = in.byte()
      if (kind == 0) { // simple path
        var i = 0
        while (i < len - 1) {
          comps(i) = Id.of(in.string())
          i += 1
        }
        comps(i) = ThisSingleton
      } else {
        var i = 0
        while (i < len) {
          val k = if (i == 0) kind else in.byte() // we already read the first kind
          comps(i) = k match {
            case 1 => Id.of(in.string())
            case 2 => Super.of(readPath(in))
            case 3 => in.readBlock {}; ThisSingleton
          }
          i += 1
        }
      }
      Path.of(comps)
    }
  }
}

object ConsistentAnalysisFormat {
  private final val EmptyTypeSingleton = EmptyType.of()
  private final val ThisSingleton = This.of()
  private final val ThisQualifierSingleton = ThisQualifier.of()
  private final val UnqualifiedSingleton = Unqualified.of()
  private final val PublicSingleton = Public.of()
  private final val DefaultCompilationTimestamp: Long = 1262304042000L // 2010-01-01T00:00:42Z

  private final val useScopes: Array[EnumSet[UseScope]] =
    Array.tabulate(8) { i =>
      val e = EnumSet.noneOf(classOf[UseScope])
      if ((i & 1) != 0) e.add(UseScope.Default)
      if ((i & 2) != 0) e.add(UseScope.Implicit)
      if ((i & 4) != 0) e.add(UseScope.PatMatTarget)
      e
    }

  private final val nameHashComparator: Comparator[NameHash] = new Comparator[NameHash] {
    def compare(o1: NameHash, o2: NameHash): Int = {
      o1.name().compareTo(o2.name()) match {
        case 0 => o1.scope().ordinal() - o2.scope().ordinal()
        case i => i
      }
    }
  }
}
