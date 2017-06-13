addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.26")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.4.0")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1")
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.3")
// addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.7.0")
addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.3")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.0-M7")

resolvers += Resolver.bintrayIvyRepo("scalacenter", "sbt-releases")
addSbtPlugin(
  ("com.thesamet" % "sbt-protoc" % "0.99.12")
    .exclude("com.trueaccord.scalapb", "protoc-bridge_2.12"))

libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin-shaded" % "0.6.0"
