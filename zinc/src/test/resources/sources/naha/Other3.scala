package naha

object Other3 {
  def implicitMember = "implicitMemberValue"
  def standardMember = "standardMemberValue"

  Other2.otherSealed match {
    case OtherSealed2 => 1
    case _ => 2
  }

}