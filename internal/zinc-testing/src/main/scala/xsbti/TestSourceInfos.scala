/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbti

import xsbti.compile.analysis.{ ReadSourceInfos, SourceInfo }

import java.util

class TestSourceInfos extends ReadSourceInfos {

  override def get(sourceFile: VirtualFileRef): SourceInfo = new TestSourceInfo

  override def getAllSourceInfos: util.Map[VirtualFileRef, SourceInfo] =
    new util.HashMap[VirtualFileRef, SourceInfo]()
}
