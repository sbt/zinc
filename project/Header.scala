import sbt._
import Keys._

import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.{ autoImport => SbtHeaderKeys }

object CustomHeaderPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin && HeaderPlugin
  override def trigger = allRequirements
  import SbtHeaderKeys.{ HeaderFileType, HeaderCommentStyle, HeaderLicense }

  override def projectSettings = Seq(
    SbtHeaderKeys.headerMappings ++= Map(
      HeaderFileType.scala -> HeaderCommentStyle.cStyleBlockComment,
      HeaderFileType.java -> HeaderCommentStyle.cStyleBlockComment
    ),
    SbtHeaderKeys.headerLicense := Some(
      HeaderLicense.Custom(
        """|Zinc - The incremental compiler for Scala.
         |Copyright Scala Center, Lightbend, and Mark Harrah
         |
         |Licensed under Apache License 2.0
         |SPDX-License-Identifier: Apache-2.0
         |
         |See the NOTICE file distributed with this work for
         |additional information regarding copyright ownership.
         |""".stripMargin
      )
    )
  )
}
