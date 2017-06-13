addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.21")
addSbtPlugin("com.geirsson" % "sbt-scalafmt" % "0.6.8")

addSbtPlugin(
  ("com.thesamet" % "sbt-protoc" % "0.99.11")
    .exclude("com.trueaccord.scalapb", "protoc-bridge_2.10"))

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin-shaded" % "0.6.0"
