package gg
package table

trait SliceTransforms[F[+ _]] extends TableModule[F]
  with ObjectConcatHelpers {
  buildNonemptyObjects(0, 1)
}

trait ObjectConcatHelpers {
  def buildNonemptyObjects(a: Int, b: Int): Unit = ()
}
