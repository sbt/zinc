package sbt
package internal
package inc

import xsbti.Logger
import xsbti.api.AnalyzedClass
import xsbti.compile.IncOptions

final class TestIncremental(log: Logger, options: IncOptions) {

  private val incremental: IncrementalCommon =
    if (options.nameHashing)
      new IncrementalNameHashing(log, options)
    else if (options.antStyle)
      new IncrementalAntStyle(log, options)
    else
      sys.error("Turning off name hashing is not supported")

  def changedIncremental(lastClassNames: collection.Set[String], oldAPI: String => AnalyzedClass,
    newAPI: String => AnalyzedClass): APIChanges =
    incremental.changedIncremental(lastClassNames, oldAPI, newAPI)

  def invalidateIncremental(previous: Relations, apis: APIs, changes: APIChanges, recompiledClasses: Set[String],
    transitive: Boolean, isScalaClass: String => Boolean): Set[String] =
    incremental.invalidateIncremental(previous, apis, changes, recompiledClasses, transitive, isScalaClass)

}
