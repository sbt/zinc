scalacOptions += "-feature"

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.0.0")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.4")
addSbtPlugin("org.scala-sbt" % "sbt-contraband" % "0.5.3")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.2")
addSbtPlugin("de.heikoseeberger" % "sbt-header" % "3.0.2")
addSbtPlugin("com.github.sbt" % "sbt-protobuf" % "0.7.1")
libraryDependencies += "com.github.os72" % "protoc-jar" % "3.11.4" // sync w/ ProtobufConfig / version
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.5.2")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "1.1.0")
