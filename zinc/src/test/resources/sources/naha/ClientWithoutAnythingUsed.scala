package naha

object ClientWithoutAnythingUsed {
  val objects = Seq(NormalDependency)
}

object ClientWithoutAnythingUsed2 {
  val strings = Seq(Other.standardMember, Other.implicitMember)
}
