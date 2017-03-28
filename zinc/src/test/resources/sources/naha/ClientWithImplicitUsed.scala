package naha

object ClientWithImplicitUsed {
  def add(nr: Any)(implicit no: ImplicitWrapper[_]) = nr.toString + no.a.toString

  val vals: Seq[Any] = Seq(NormalDependecy.implicitMember, NormalDependecy.standardMember)

  import WithImplicits._
  vals.map(add)
}