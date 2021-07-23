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

package sbt.internal.inc.text

import java.io.{ BufferedReader, Writer }

import sbt.internal.inc.{ ExternalDependencies, InternalDependencies, Relations, UsedNames }
import sbt.internal.util.Relation
import xsbti.VirtualFileRef
import xsbti.api.DependencyContext._

trait RelationsTextFormat extends FormatCommons {

  def sourcesMapper: Mapper[VirtualFileRef]
  def binariesMapper: Mapper[VirtualFileRef]
  def productsMapper: Mapper[VirtualFileRef]

  private case class Descriptor[A, B](
      header: String,
      selectCorresponding: Relations => scala.collection.Map[A, scala.collection.Set[B]],
      keyMapper: Mapper[A],
      valueMapper: Mapper[B]
  )

  private def descriptor[A, B](
      header: String,
      rels: Relations => Relation[A, B],
      keyMapper: Mapper[A],
      valueMapper: Mapper[B]
  ) =
    Descriptor(header, rels.andThen(_.forwardMap), keyMapper, valueMapper)

  private def stringsDescriptor(header: String, rels: Relations => Relation[String, String]) =
    descriptor(header, rels, Mapper.forString, Mapper.forString)

  private val allRelations: List[Descriptor[_, _]] = {
    List(
      descriptor("products", _.srcProd, sourcesMapper, productsMapper),
      descriptor("library dependencies", _.libraryDep, sourcesMapper, binariesMapper),
      descriptor("library class names", _.libraryClassName, binariesMapper, Mapper.forString),
      stringsDescriptor("member reference internal dependencies", _.memberRef.internal),
      stringsDescriptor("member reference external dependencies", _.memberRef.external),
      stringsDescriptor("inheritance internal dependencies", _.inheritance.internal),
      stringsDescriptor("inheritance external dependencies", _.inheritance.external),
      stringsDescriptor("local internal inheritance dependencies", _.localInheritance.internal),
      stringsDescriptor("local external inheritance dependencies", _.localInheritance.external),
      descriptor("class names", _.classes, sourcesMapper, Mapper.forString),
      Descriptor("used names", _.names.toMultiMap, Mapper.forString, Mapper.forUsedName),
      stringsDescriptor("product class names", _.productClassName)
    )
  }

  protected object RelationsF {
    def write(out: Writer, relations: Relations): Unit = {
      def writeRelation[A, B](relDesc: Descriptor[A, B]): Unit = {
        import relDesc._
        val map = selectCorresponding(relations)
        writeHeader(out, header)
        writeSize(out, map.valuesIterator.flatten.size)
        // We sort for ease of debugging and for more efficient reconstruction when reading.
        // Note that we don't share code with writeMap. Each is implemented more efficiently
        // than the shared code would be, and the difference is measurable on large analyses.
        val kvs = map.iterator.map { case (k, vs) => keyMapper.write(k) -> vs }.toSeq.sortBy(_._1)
        for ((k, vs) <- kvs; v <- vs.iterator.map(valueMapper.write).toSeq.sorted) {
          out.write(k); out.write(" -> "); out.write(v); out.write("\n")
        }
      }
      allRelations.foreach(writeRelation(_))
    }

    def read(in: BufferedReader): Relations = {
      def readRelation[A, B](relDesc: Descriptor[A, B]): Map[A, Set[B]] = {
        import relDesc._
        val items = readPairs(in)(header, keyMapper.read, valueMapper.read).toIterator
        // Reconstruct the multi-map efficiently, using the writing strategy above
        val builder = Map.newBuilder[A, Set[B]]
        var currentKey = null.asInstanceOf[A]
        var currentVals = Set.newBuilder[B]
        def closeEntry() = if (currentKey != null) builder += ((currentKey, currentVals.result()))
        while (items.hasNext) {
          val (key, value) = items.next()
          if (key == currentKey) currentVals += value
          else {
            closeEntry()
            currentKey = key
            currentVals = Set.newBuilder[B] += value
          }
        }
        closeEntry()
        builder.result()
      }
      construct(allRelations.map(readRelation(_)))
    }
  }

  /**
   * Reconstructs a Relations from a list of Relation
   * The order in which the relations are read matters and is defined by `existingRelations`.
   */
  private def construct(relations: List[Map[_, Set[_]]]) =
    relations match {
      case p :: bin :: lcn :: mri :: mre :: ii :: ie :: lii :: lie :: cn :: un :: bcn :: Nil =>
        def toMultiMap[K, V](m: Map[_, _]): Map[K, Set[V]] = m.asInstanceOf[Map[K, Set[V]]]
        def toRelation[K, V](m: Map[_, _]): Relation[K, V] = Relation.reconstruct(toMultiMap(m))

        val internal = InternalDependencies(
          Map(
            DependencyByMemberRef -> toRelation(mri),
            DependencyByInheritance -> toRelation(ii),
            LocalDependencyByInheritance -> toRelation(lii),
          )
        )
        val external = ExternalDependencies(
          Map(
            DependencyByMemberRef -> toRelation(mre),
            DependencyByInheritance -> toRelation(ie),
            LocalDependencyByInheritance -> toRelation(lie),
          )
        )
        Relations.make(
          toRelation(p),
          toRelation(bin),
          toRelation(lcn),
          internal,
          external,
          toRelation(cn),
          UsedNames.fromMultiMap(toMultiMap(un)),
          toRelation(bcn),
        )
      case _ =>
        throw new java.io.IOException(
          s"Expected to read ${allRelations.length} relations but read ${relations.length}."
        )
    }
}
