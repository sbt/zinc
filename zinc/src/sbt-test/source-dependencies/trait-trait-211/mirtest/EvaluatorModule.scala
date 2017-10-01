package xx

import gg._

// note changing the parent from TableModule to ColumnarTableModule here changes the result.
trait EvaluatorModule[F[+ _]] extends TableModule[F] {
  def eval: String = "x"
}
