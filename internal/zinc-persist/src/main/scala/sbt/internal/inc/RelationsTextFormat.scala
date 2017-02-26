/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.internal.inc

import java.io.{ File, BufferedReader, Writer }

import sbt.internal.util.Relation
import xsbti.api.DependencyContext._

trait RelationsTextFormat extends FormatCommons {

  def mappers: AnalysisMappers

  private case class Descriptor[A, B](
    header: String,
    selectCorresponding: Relations => Relation[A, B],
    keyMapper: Mapper[A],
    valueMapper: Mapper[B]
  )

  private def stringsDescriptor(header: String, rels: Relations => Relation[String, String]) =
    Descriptor(header, rels, Mapper.forString, Mapper.forString)

  private val allRelations: List[Descriptor[_, _]] = {
    List(
      Descriptor("products", _.srcProd, mappers.sourceMapper, mappers.productMapper),
      Descriptor("library dependencies", _.libraryDep, mappers.sourceMapper, mappers.binaryMapper),
      Descriptor("library class names", _.libraryClassName, mappers.binaryMapper, Mapper.forString),
      stringsDescriptor("member reference internal dependencies", _.memberRef.internal),
      stringsDescriptor("member reference external dependencies", _.memberRef.external),
      stringsDescriptor("inheritance internal dependencies", _.inheritance.internal),
      stringsDescriptor("inheritance external dependencies", _.inheritance.external),
      stringsDescriptor("local internal inheritance dependencies", _.localInheritance.internal),
      stringsDescriptor("local external inheritance dependencies", _.localInheritance.external),
      Descriptor("class names", _.classes, mappers.sourceMapper, Mapper.forString),
      stringsDescriptor("used names", _.names),
      stringsDescriptor("product class names", _.productClassName)
    )
  }

  protected object RelationsF {

    def write(out: Writer, relations: Relations): Unit = {
      def writeRelation[A, B](relDesc: Descriptor[A, B], relations: Relations): Unit = {
        // This ordering is used to persist all values in order. Since all values will be
        // persisted using their string representation, it makes sense to sort them using
        // their string representation.
        val toStringOrdA = new Ordering[A] {
          def compare(a: A, b: A) = relDesc.keyMapper.write(a) compare relDesc.keyMapper.write(b)
        }
        val toStringOrdB = new Ordering[B] {
          def compare(a: B, b: B) = relDesc.valueMapper.write(a) compare relDesc.valueMapper.write(b)
        }
        val header = relDesc.header
        val rel = relDesc.selectCorresponding(relations)
        writeHeader(out, header)
        writeSize(out, rel.size)
        // We sort for ease of debugging and for more efficient reconstruction when reading.
        // Note that we don't share code with writeMap. Each is implemented more efficiently
        // than the shared code would be, and the difference is measurable on large analyses.
        rel.forwardMap.toSeq.sortBy(_._1)(toStringOrdA).foreach {
          case (k, vs) =>
            val kStr = relDesc.keyMapper.write(k)
            vs.toSeq.sorted(toStringOrdB) foreach { v =>
              out.write(kStr); out.write(" -> "); out.write(relDesc.valueMapper.write(v)); out.write("\n")
            }
        }
      }

      allRelations.foreach { relDesc => writeRelation(relDesc, relations) }
    }

    def read(in: BufferedReader): Relations = {
      def readRelation[A, B](relDesc: Descriptor[A, B]): Relation[A, B] = {
        val expectedHeader = relDesc.header
        val items = readPairs(in)(expectedHeader, relDesc.keyMapper.read, relDesc.valueMapper.read).toIterator
        // Reconstruct the forward map. This is more efficient than Relation.empty ++ items.
        var forward: List[(A, Set[B])] = Nil
        var currentItem: (A, B) = null
        var currentKey: A = null.asInstanceOf[A]
        var currentVals: List[B] = Nil
        def closeEntry(): Unit = {
          if (currentKey != null) forward = (currentKey, currentVals.toSet) :: forward
          currentKey = currentItem._1
          currentVals = currentItem._2 :: Nil
        }
        while (items.hasNext) {
          currentItem = items.next()
          if (currentItem._1 == currentKey) currentVals = currentItem._2 :: currentVals else closeEntry()
        }
        if (currentItem != null) closeEntry()
        Relation.reconstruct(forward.toMap)
      }

      val relations = allRelations.map(rd => readRelation(rd))

      construct(relations)
    }
  }

  /**
   * Reconstructs a Relations from a list of Relation
   * The order in which the relations are read matters and is defined by `existingRelations`.
   */
  private def construct(relations: List[Relation[_, _]]) =
    relations match {
      case p :: bin :: lcn :: mri :: mre :: ii :: ie :: lii :: lie :: cn :: un :: bcn :: Nil =>
        val srcProd = p.asInstanceOf[Relation[File, File]]
        val binaryDep = bin.asInstanceOf[Relation[File, File]]
        val libraryClassName = lcn.asInstanceOf[Relation[File, String]]
        val classes = cn.asInstanceOf[Relation[File, String]]
        val names = un.asInstanceOf[Relation[String, String]]
        val binaryClassName = bcn.asInstanceOf[Relation[String, String]]

        val internal = InternalDependencies(Map(
          DependencyByMemberRef -> mri.asInstanceOf[Relation[String, String]],
          DependencyByInheritance -> ii.asInstanceOf[Relation[String, String]],
          LocalDependencyByInheritance -> lii.asInstanceOf[Relation[String, String]]
        ))
        val external = ExternalDependencies(Map(
          DependencyByMemberRef -> mre.asInstanceOf[Relation[String, String]],
          DependencyByInheritance -> ie.asInstanceOf[Relation[String, String]],
          LocalDependencyByInheritance -> lie.asInstanceOf[Relation[String, String]]
        ))
        Relations.make(srcProd, binaryDep, libraryClassName, internal, external, classes, names, binaryClassName)
      case _ => throw new java.io.IOException(s"Expected to read ${allRelations.length} relations but read ${relations.length}.")
    }
}
