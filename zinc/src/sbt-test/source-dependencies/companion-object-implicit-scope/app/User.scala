object User {
  val s: String = implicitly[Pretty[A]].show(new D)
}
