import sbt._
import Keys._

import de.heikoseeberger.sbtheader.{ HeaderPlugin, HeaderPattern }
import HeaderPlugin.autoImport._

object CustomHeaderPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin && HeaderPlugin
  override def trigger = allRequirements

  override def projectSettings = Seq(
    headers := Map(
      "scala" -> (HeaderPattern.cStyleBlockComment, copyrightText),
      "java" -> (HeaderPattern.cStyleBlockComment, copyrightText)
    )
  )

  val copyrightText =
    """|/*
       | * Zinc - The incremental compiler for Scala.
       | * Copyright 2011 - 2017, Lightbend, Inc.
       | * Copyright 2008 - 2010, Mark Harrah
       | * This software is released under the terms written in LICENSE.
       | */
       |
       |""".stripMargin
}
