package xx

import gg.table._

trait EvaluatorSpecification[F[+_]] extends EvaluatorTestSupport[F] { self =>
}

trait EvaluatorTestSupport[F[+_]] extends EvaluatorModule[F]
    with ColumnarTableModule[F] { outer =>

  def consumeEval: String = eval
}
