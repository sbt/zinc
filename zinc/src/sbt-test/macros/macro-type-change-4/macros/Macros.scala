package Macros

import scala.language.experimental.macros

import scala.reflect.macros.blackbox.Context

object Macros {
  def hasAnyField[T](placeholder: Boolean): Boolean = macro MacrosImpl.hasAnyFieldImpl[T]

  object MacrosImpl {
    def hasAnyFieldImpl[T: c.WeakTypeTag](c: Context)(placeholder: c.Expr[Boolean]): c.Expr[Boolean] = {
      import c.universe._

      val tpe = weakTypeOf[T]
      val hasField = tpe.members.exists {
        case m: TermSymbol => m.isVal || m.isVar
        case _ => false
      }

      c.Expr[Boolean](q"$hasField")
    }
  }
}