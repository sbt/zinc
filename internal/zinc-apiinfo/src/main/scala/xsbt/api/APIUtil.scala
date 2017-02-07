package xsbt.api

import xsbti.api._
import scala.collection.mutable.HashSet

object APIUtil {
  val modifiersToByte = (m: Modifiers) => {
    import m._
    def x(b: Boolean, bit: Int) = if (b) 1 << bit else 0
    (x(isAbstract, 0) | x(isOverride, 1) | x(isFinal, 2) | x(isSealed, 3) | x(isImplicit, 4) | x(isLazy, 5) | x(isMacro, 6)).toByte
  }
  val byteToModifiers = (b: Byte) => {
    def x(bit: Int) = (b & (1 << bit)) != 0
    new Modifiers(x(0), x(1), x(2), x(3), x(4), x(5), x(6), x(7))
  }

  def isScalaSourceName(name: String): Boolean = name.endsWith(".scala")

  def hasMacro(c: ClassLike): Boolean =
    {
      val check = new HasMacro
      check.visitDefinition(c)
      check.hasMacro
    }

  private[this] class HasMacro extends Visit {
    var hasMacro = false

    // Don't visit inherited definitions since we consider that a class
    // that inherits a macro does not have a macro.
    override def visitStructure0(structure: Structure): Unit = {
      visitTypes(structure.parents)
      visitDefinitions(structure.declared.toArray[Definition])
    }

    override def visitModifiers(m: Modifiers): Unit = {
      hasMacro ||= m.isMacro
      super.visitModifiers(m)
    }
  }

  def minimize(api: ClassLike): ClassLike =
    minimizeClass(api)
  def minimizeDefinitions(ds: Array[Definition]): Array[Definition] =
    ds flatMap minimizeDefinition
  def minimizeDefinition(d: Definition): Array[Definition] =
    d match {
      case c: ClassLike => Array(minimizeClass(c))
      case _            => Array()
    }
  def minimizeClass(c: ClassLike): ClassLike =
    {
      val savedAnnotations = Discovery.defAnnotations(c.structure, (_: Any) => true).toArray[String]
      val struct = minimizeStructure(c.structure, c.definitionType == DefinitionType.Module)
      new ClassLike(c.name, c.access, c.modifiers, c.annotations,
        c.definitionType, lzy(emptyType), lzy(struct), savedAnnotations, c.childrenOfSealedClass,
        c.topLevel, c.typeParameters)
    }

  def minimizeStructure(s: Structure, isModule: Boolean): Structure =
    new Structure(lzy(s.parents), filterDefinitions(s.declared, isModule), filterDefinitions(s.inherited, isModule))
  def filterDefinitions(ds: Array[ClassDefinition], isModule: Boolean): Lazy[Array[ClassDefinition]] =
    lzy(if (isModule) ds filter Discovery.isMainMethod else Array())

  def isNonPrivate(d: Definition): Boolean = isNonPrivate(d.access)
  /** Returns false if the `access` is `Private` and qualified, true otherwise.*/
  def isNonPrivate(access: Access): Boolean =
    access match {
      case p: Private if !p.qualifier.isInstanceOf[IdQualifier] => false
      case _ => true
    }
  private val emptyModifiers = new Modifiers(false, false, false, false, false, false, false, false)
  private val emptyStructure = new Structure(lzy(Array.empty), lzy(Array.empty), lzy(Array.empty))
  def emptyClassLike(name: String, definitionType: DefinitionType): ClassLike =
    new xsbti.api.ClassLike(name, new Public, emptyModifiers, Array.empty,
      definitionType, lzy(emptyType), lzy(emptyStructure), Array.empty, Array.empty, true, Array.empty)

  private[this] def lzy[T <: AnyRef](t: T): Lazy[T] = SafeLazyProxy.strict(t)

  private[this] val emptyType = new EmptyType
}
