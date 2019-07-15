scalaVersion := "2.12.8"

addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.9")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.0")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.4.4")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12-rc5")
libraryDependencies += // Remember to remove the explicit dependency on java-protobuf:3.7.0 when updating this dependency
  "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0"
addSbtPlugin("com.lightbend" % "sbt-whitesource" % "0.1.13")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
