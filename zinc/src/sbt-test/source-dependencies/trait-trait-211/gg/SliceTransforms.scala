package gg
package table

trait SliceTransforms[F[+ _]] extends TableModule[F]
  with ObjectConcatHelpers {
  buildNonemptyObjects(0)
}

trait ObjectConcatHelpers {
  def buildNonemptyObjects(a: Int): Unit = ()
}
