package naha

object Other {
  def implicitMember = "implicitMemberValue"
  def standardMember = "standardMemberValue"
}

trait MarkerTrait

sealed class OtherSealed
object OtherSealed1 extends OtherSealed
object OtherSealed2 extends OtherSealed
