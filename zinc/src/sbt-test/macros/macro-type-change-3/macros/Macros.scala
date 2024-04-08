package Macros

import scala.language.experimental.macros

import scala.reflect.macros.blackbox.Context

object Macros {
  def hasAnyField[T]: Boolean = macro hasAnyFieldImpl[T]

  def hasAnyFieldImpl[T: c.WeakTypeTag](c: Context): c.Expr[Boolean] = {
    import c.universe._

    val hasField = weakTypeOf[T].baseClasses.flatMap(base =>
      base.asType.toType.decls.collect {
        case m: TermSymbol if m.isVal || m.isVar => m
      }
    ).nonEmpty

    c.Expr[Boolean](Literal(Constant(hasField)))
  }
}
