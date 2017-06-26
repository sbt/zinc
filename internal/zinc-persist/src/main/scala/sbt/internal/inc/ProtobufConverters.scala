package sbt.internal.inc

import java.io.File

import xsbti.compile.analysis.Stamp

object ProtobufConverters {
  def toSchema(stamp: Stamp): schema.StampType = {
    val s0 = schema.StampType()
    stamp match {
      case hash: Hash       => s0.withHash(schema.Hash(hash = hash.hexHash))
      case lm: LastModified => s0.withLastModified(schema.LastModified(millis = lm.value))
      case _: Stamp         => s0
    }
  }

  def toSchemaMap(data: Map[File, Stamp],
                  fileMapper: Mapper[File]): Map[String, schema.StampType] =
    data.map(kv => fileMapper.write(kv._1) -> toSchema(kv._2))
}
