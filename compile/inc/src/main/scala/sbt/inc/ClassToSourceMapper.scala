package sbt.inc

import java.io.File

import sbt.Relation

/**
 * Maps class-based dependencies to source dependencies using `declaredClasses` relation.
 *
 * The mapping is performed using two relations that track declared classes before
 * and after recompilation of sources. This way, we can properly map dependencies
 * on classes that have been moved between source files. In such case, a single
 * class can be mapped to two different source files.
 */
class ClassToSourceMapper(previousRelations: Relations, recompiledRelations: Relations) {

  def toSrcFile(className: String): Set[File] = {
    val srcs = previousRelations.declaredClasses.reverse(className) ++
      recompiledRelations.declaredClasses.reverse(className)
    if (srcs.isEmpty == 0)
      sys.error(s"No entry for class $className in declaredClasses relation.")
    else
      srcs
  }

  def convertToSrcDependency(classDependency: Relation[String, String]): Relation[File, File] = {
    def convertRelationMap(m: Map[String, Set[String]]): Map[File, Set[File]] =
      m.toSeq.flatMap {
        case (key, values) =>
          val keySrcs = toSrcFile(key)
          val valueSrcs = values.map(toSrcFile)
          keySrcs.flatMap(keySrc => valueSrcs.map(keySrc -> _))
      }.toMap
    val forwardMap = convertRelationMap(classDependency.forwardMap)
    val reverseMap = convertRelationMap(classDependency.reverseMap)
    Relation.make(forwardMap, reverseMap)
  }

}
