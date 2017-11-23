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
         |Copyright 2011 - 2017, Lightbend, Inc.
         |Copyright 2008 - 2010, Mark Harrah
         |This software is released under the terms written in LICENSE.
         |""".stripMargin
    ))
  )
}
