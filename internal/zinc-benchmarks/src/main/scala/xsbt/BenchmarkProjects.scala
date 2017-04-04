package xsbt

object BenchmarkProjects {
  object Shapeless
    extends BenchmarkProject(
      "milessabin/shapeless",
      "fb109614dd6efbb97c5b4f164b7adfc284982b25",
      List("coreJVM")
    )

  object Scalac
    extends BenchmarkProject(
      "scala/scala",
      "827d69d48e96d9add75ce19e06b374610784c936",
      List("library")
    )
}
