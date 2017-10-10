package xx

object Hello extends App {
  val consumer = new StringLibSpecs
  consumer.transform
}

class StringLibSpecs extends EvaluatorSpecification
