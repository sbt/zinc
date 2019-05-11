import sbt._
import Keys._

import de.heikoseeberger.sbtheader.HeaderPlugin
import de.heikoseeberger.sbtheader.HeaderPlugin.{autoImport => SbtHeaderKeys}

object CustomHeaderPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin && HeaderPlugin
  override def trigger = allRequirements
  import SbtHeaderKeys.{HeaderFileType, HeaderCommentStyle, HeaderLicense}

  override def projectSettings = Seq(
    SbtHeaderKeys.headerMappings ++= Map(
      HeaderFileType.scala -> HeaderCommentStyle.CStyleBlockComment,
      HeaderFileType.java -> HeaderCommentStyle.CStyleBlockComment
    ),
    SbtHeaderKeys.headerLicense := Some(HeaderLicense.Custom(
      """|Zinc - The incremental compiler for Scala.
         |Copyright Lightbend, Inc. and Mark Harrah
         |
         |Licensed under Apache License 2.0
         |(http://www.apache.org/licenses/LICENSE-2.0).
         |
         |See the NOTICE file distributed with this work for
         |additional information regarding copyright ownership.
         |""".stripMargin
    ))
  )
}
