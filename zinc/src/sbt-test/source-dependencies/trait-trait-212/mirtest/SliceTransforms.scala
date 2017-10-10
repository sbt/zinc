package gg
package table

trait SliceTransforms {
  def transform: Unit = {
    buildNonemptyObjects(0)
  }

  def buildNonemptyObjects(a: Int): Unit = ()
}
