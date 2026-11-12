import scala.language.dynamics

class D extends Dynamic {
  def selectDynamic(name: String): String = "dynamic"
  def foo: String = "static"
}
