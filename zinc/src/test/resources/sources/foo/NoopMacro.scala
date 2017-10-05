package foo

import scala.reflect.macros.blackbox.Context

class NoopMacro(val c: Context){
  def noop(arg: c.Tree): c.Tree = arg
}