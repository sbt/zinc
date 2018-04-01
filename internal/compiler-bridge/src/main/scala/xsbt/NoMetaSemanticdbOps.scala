/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbt

import scala.reflect.NameTransformer
import scala.tools.nsc.Global

class NoMetaSemanticdbOps[GlobalType <: Global](g: GlobalType) {

  implicit class XtensionGSymbol(sym: GlobalType#Symbol) {

    /** This converts `scala.reflect.internal.Symbols.Symbol` to a string according to the naming convention of Semanticdb.
     *
     * The following is unimplemented:
     *   - Local symbol. (returns empty string)
     *   - Distinguish overloaded methods that a same name but a different package.
     *
     * This is copy from [[https://github.com/scalameta/scalameta/blob/v3.7.3/semanticdb/scalac/library/src/main/scala/scala/meta/internal/semanticdb/scalac/SymbolOps.scala#L13-L47]]
     */
    def toSemanticName: String = {
      if (sym == null || sym == g.NoSymbol) return g.nme.NO_NAME.toString
      // Whether or not the symbol is an overloaded method should be determined by the caller
      // `if (sym.isOverloaded) sym.alternatives.map(_.toSemanticName)`
      if (sym.isModuleClass) return sym.asClass.module.toSemanticName
      if (sym.isTypeSkolem) return sym.deSkolemize.toSemanticName

      if (isSemanticdbLocal(sym)) return "" // local definition returns empty string

      var b: java.lang.StringBuffer = null

      def loop(size: Int, _sym: GlobalType#Symbol): Unit = {
        val symName = _sym.name
        val nSize = symName.length - (if (symName.endsWith(g.nme.LOCAL_SUFFIX_STRING)) 1
                                      else 0)

        val (prefix, suffix) =
          if (_sym.isMethod) {
            val c = if (_sym.isConstructor) "`" else ""
            (c, c + disambiguator(_sym))
          } else if (_sym.isTypeParameter || _sym.isTypeSkolem) ("[", "]")
          else if (_sym.isValueParameter) ("(", ")")
          else if (_sym.hasPackageFlag) ("", ".")
          else if (_sym.isType || _sym.isJavaDefined &&
                   (_sym.isClass || _sym.isModule)) ("", "#")
          else ("", ".")

        if (_sym.isRoot || _sym.isRootPackage || _sym == g.NoSymbol || _sym.owner.isEmptyPackage || _sym.owner.isEffectiveRoot) {
          val isRoot = _sym.owner.isRoot
          val prePrefix = if (isRoot) "" else "_empty_."
          val capacity = size + nSize + prePrefix.length + prefix.length + suffix.length
          b = new java.lang.StringBuffer(capacity)
          b.append(prePrefix)
        } else {
          val owner =
            if (_sym.isTypeParameter || _sym.isParameter || _sym.owner.isConstructor)
              _sym.owner
            else _sym.effectiveOwner.enclClass
          loop(size + nSize + prefix.length + suffix.length, owner)
        }
        b.append(prefix)
        b.append(g.chrs, symName.start, nSize)
        b.append(suffix)
        ()
      }

      loop(0, sym)
      b.toString
    }
  }

  @inline
  private def isSemanticdbLocal(sym: GlobalType#Symbol): Boolean = {
    def definitelyGlobal = sym.hasPackageFlag
    def definitelyLocal(sym: GlobalType#Symbol) =
      sym.owner.isTerm || sym.owner.isRefinementClass || isSelfParameter(sym)
    !definitelyGlobal && !sym.isParameter &&
    sym.ownersIterator.exists(definitelyLocal)
  }

  @inline
  private def disambiguator(sym: GlobalType#Symbol) = {
    //TODO: Different packages but the same name class need to be distinguished.
    "(" + typeDescriptor(sym.info) + ")."
  }

  @inline
  private def isSelfParameter(sym: GlobalType#Symbol): Boolean = {
    sym != g.NoSymbol && sym.owner.thisSym == sym
  }

  // copy form https://github.com/scalameta/scalameta/blob/v3.7.3/semanticdb/scalac/library/src/main/scala/scala/meta/internal/semanticdb/scalac/TypeOps.scala#L202-L219
  @inline
  private def typeDescriptor(tpe: GlobalType#Type): String = {
    if (g.definitions.isByNameParamType(tpe.asInstanceOf[g.Type])) {
      "=>" + tpe.typeArgs.headOption.getOrElse(g.typeOf[Nothing]).typeSymbol.nameString
    } else if (g.definitions.isRepeatedParamType(tpe.asInstanceOf[g.Type])) {
      tpe.typeArgs.headOption.getOrElse(g.typeOf[Nothing]).typeSymbol.nameString + "*"
    } else
      tpe match {
        case g.TypeRef(_, gsym: g.Symbol, _) =>
          // It is simplified because the package symbol does not come
          gsym.name.decoded.stripSuffix(g.nme.LOCAL_SUFFIX_STRING)
        case g.SingleType(_, _)                    => ".type"
        case g.ThisType(_)                         => ".type"
        case g.ConstantType(g.Constant(_: g.Type)) => "Class"
        case g.ConstantType(_)                     => ".type"
        case g.RefinedType(_, _)                   => "{}"
        case _: g.AnnotatedType                    => typeDescriptor(tpe.underlying)
        case g.ExistentialType(_, gtpe: g.Type)    => typeDescriptor(gtpe)
        case _: g.NullaryMethodType | _: g.MethodType =>
          tpe.paramss.flatten.map(p => typeDescriptor(p.info)).mkString(",")
        case g.PolyType(_, gtpe: g.Type) => typeDescriptor(gtpe)
        case _                           => "?"
      }
  }

}
