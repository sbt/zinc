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

import java.io.File
import java.nio.file.{ Path, Paths }

import sbt.internal.inc.UsedName
import xsbti.{ UseScope, VirtualFileRef }
import xsbti.compile.analysis.Stamp
import scala.collection.JavaConverters._

case class Mapper[V](read: String => V, write: V => String)
case class ContextAwareMapper[C, V](read: (C, String) => V, write: (C, V) => String)

object Mapper {
  val forPath: Mapper[Path] = Mapper(Paths.get(_), _.toString)
  val forFile: Mapper[File] = Mapper(FormatCommons.stringToFile, FormatCommons.fileToString)
  val forFileV: Mapper[VirtualFileRef] =
    Mapper(FormatCommons.stringToFileV, FormatCommons.fileVToString)
  val forString: Mapper[String] = Mapper(identity, identity)
  val forStampV: ContextAwareMapper[VirtualFileRef, Stamp] =
    ContextAwareMapper((_, v) => sbt.internal.inc.Stamp.fromString(v), (_, s) => s.toString)
  val forUsedName: Mapper[UsedName] = {
    val enumSetSerializer = EnumSetSerializer(UseScope.values())
    def serialize(usedName: UsedName): String =
      s"${enumSetSerializer.serialize(usedName.scopes)}${usedName.name}"

    def deserialize(s: String) = UsedName(s.tail, enumSetSerializer.deserialize(s.head).asScala)

    Mapper(deserialize, serialize)
  }

  implicit class MapperOpts[V](mapper: Mapper[V]) {
    def map[T](map: V => T, unmap: T => V) =
      Mapper[T](mapper.read.andThen(map), unmap.andThen(mapper.write))
  }
}
