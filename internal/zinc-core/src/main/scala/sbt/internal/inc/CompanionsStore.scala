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
