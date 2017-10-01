package gg
package table

trait ColumnarTableModule[F[+ _]] extends TableModule[F]
  with SliceTransforms[F]
