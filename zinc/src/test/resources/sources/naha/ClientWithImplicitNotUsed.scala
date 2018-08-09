package naha

object ClientWithImplicitNotUsed {
  Seq(NormalDependency.implicitMember, NormalDependency.standardMember, WithImplicits.standardMember)
}