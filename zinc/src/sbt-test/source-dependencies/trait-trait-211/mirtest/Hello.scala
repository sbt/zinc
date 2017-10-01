package xx

import gg.table._

object Hello extends App {
  def testEval: Unit = {
    val consumer = new StringLibSpecs[Option] {}
    consumer.consumeEval
  }
  testEval
}

trait StringLibSpecs[F[+_]] extends EvaluatorSpecification[F] { self =>
}
