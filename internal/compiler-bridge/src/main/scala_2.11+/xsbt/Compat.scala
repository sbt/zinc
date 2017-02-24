package xsbt

abstract class Compat
object Compat

trait CachedCompilerCompat { self: CachedCompiler0 =>
  def newCompiler: Compiler = new Compiler()
}
