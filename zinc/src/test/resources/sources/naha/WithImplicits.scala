package naha

object WithImplicits {
  implicit def implicitMember = ImplicitWrapper("implicitMemberValue")
  def standardMember = "standardMemberValue"
}

case class ImplicitWrapper[T](a: T)
