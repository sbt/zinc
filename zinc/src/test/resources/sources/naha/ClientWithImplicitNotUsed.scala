package naha

object ClientWithImplicitNotUsed {
  Seq(NormalDependecy.implicitMember, NormalDependecy.standardMember, WithImplicits.standardMember)
}