scalacOptions += "-feature"

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.0.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.2")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.6.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.7")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.4")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.12.0")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.2.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.2")
