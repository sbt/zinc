addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.2.27")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.1")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0-M1")
// drop scalafmt on the 1.0.0 branch to dogfood 1.0.0-RC2 before there is a sbt 1.0 of new-sbt-scalafnt
//  see https://github.com/lucidsoftware/neo-sbt-scalafmt/pull/34
// addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.3")
// addSbtPlugin("de.heikoseeberger" % "sbt-header" % "1.7.0")
addSbtPlugin("org.scala-sbt" % "sbt-houserules" % "0.3.3")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.3.0-M9")
addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12-rc4")
libraryDependencies += "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.0"
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.15")
