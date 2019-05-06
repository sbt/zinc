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

package sbt
package internal
package inc

import xsbti.api._

// This intentionally provides only get() since set() needs to be part of
// the zip file created for Analysis store.
trait CompanionsStore {
  def get(): Option[(Map[String, Companions], Map[String, Companions])]
  def getUncaught(): (Map[String, Companions], Map[String, Companions])
}
