package sbt.internal.inc

import java.util.Optional

import xsbti.T2

object JavaInterfaceUtil {
  private[sbt] implicit class PimpSbtTuple[T, U](sbtTuple: T2[T, U]) {
    def toScalaTuple: (T, U) = sbtTuple.get1() -> sbtTuple.get2()
  }

  private[sbt] implicit class PimpOptional[T](optional: Optional[T]) {
    def toOption: Option[T] = if (!optional.isPresent) None else Some(optional.get())
  }

  private[sbt] implicit class PimpOption[T](option: Option[T]) {
    def toOptional: Optional[T] = option match {
      case Some(value) => Optional.of(value)
      case None        => Optional.empty[T]
    }
  }
}
