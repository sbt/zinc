package naha

object ClientWithoutImplicit {
  Seq(NormalDependency.standardMember, WithImplicits.standardMember)
}