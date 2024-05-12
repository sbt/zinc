package lib

import scala.language.experimental.macros

import scala.reflect.macros.blackbox.Context

object Macro {
  def append(s: String): String = macro impl

  def impl(c: Context)(s: c.Tree): c.Tree = {
    import c.universe._
    val suffix = new Access().give
    q"""$s + $suffix"""
  }
}
