import sbt.zinc.example.Baz

object `package` {
  implicit val b = Baz(55)
}
