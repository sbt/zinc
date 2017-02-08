/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
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
