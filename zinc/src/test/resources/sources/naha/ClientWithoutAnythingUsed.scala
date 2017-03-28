package naha

object ClientWithoutAnythingUsed {
  val objects = Seq(NormalDependecy)
}

object ClientWithoutAnythingUsed2 {
  val strings = Seq(Other.standardMember, Other.implicitMember)
}
