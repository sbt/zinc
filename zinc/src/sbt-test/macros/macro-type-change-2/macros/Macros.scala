package Macros

import scala.language.experimental.macros

import scala.reflect.macros.blackbox.Context

object Macros {
  def hasAnyField[T]: Boolean = macro hasAnyFieldImpl[T]

  def hasAnyFieldImpl[T: c.WeakTypeTag](c: Context): c.Expr[Boolean] = {
    import c.universe._

    val hasField = weakTypeOf[T].members.exists {
      case m: TermSymbol => m.isVal || m.isVar
      case _ => false
    }

    c.Expr[Boolean](Literal(Constant(hasField)))
  }
}