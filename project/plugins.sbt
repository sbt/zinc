scalacOptions += "-feature"

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.3")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.6.5")
addSbtPlugin("com.github.gseitz" % "sbt-protobuf" % "0.6.5")
libraryDependencies += "com.github.os72" % "protoc-jar" % "3.11.4" // sync w/ ProtobufConfig / version
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.0")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.2.0")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.1")
