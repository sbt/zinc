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

package sbt
package internal
package inc

import java.util.Optional
import sbt.util.InterfaceUtil
import xsbti.{ DiagnosticCode, DiagnosticRelatedInformation, Position }

object DiagnosticsUtil {
  def diagnosticCode(code: String, explanation: Option[String]): DiagnosticCode =
    new ConcreteDiagnosticCode(code, explanation)

  def diagnosticRelatedInformation(
      position: Position,
      message: String
  ): DiagnosticRelatedInformation =
    new ConcreteDiagnosticRelatedInformation(position, message)

  private class ConcreteDiagnosticCode(code0: String, explanation0: Option[String])
      extends DiagnosticCode {
    override val code: String = code0
    override val explanation: Optional[String] = InterfaceUtil.o2jo(explanation0)
  }

  private class ConcreteDiagnosticRelatedInformation(position0: Position, message0: String)
      extends DiagnosticRelatedInformation {
    override val position: Position = position0
    override val message: String = message0
  }
}
