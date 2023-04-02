scalacOptions += "-feature"

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.2.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.3")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.4")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.9.0")
addSbtPlugin("com.github.sbt" % "sbt-protobuf" % "0.7.2")
libraryDependencies += "com.github.os72" % "protoc-jar" % "3.11.4" // sync w/ ProtobufConfig / version
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.2")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.9.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")
addSbtPlugin("org.jetbrains.scala" % "sbt-ide-settings" % "1.1.1")
