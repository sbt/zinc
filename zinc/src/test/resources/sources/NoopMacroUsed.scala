import foo.NoopMacro // this import pull nonexisting object based on class NoopMacro
import scala.language.experimental.macros

class NoopMacroUsed {
  def noop(arg: Int): Int = macro NoopMacro.noop
}