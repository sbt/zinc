package sbt.inc.binary.converter

import org.scalatest.funsuite.AnyFunSuite
import sbt.internal.inc.binary.converters.InternalApiProxy

class InternalApiProxySpecification extends AnyFunSuite {
  test("should create Modifiers from tags") {
    val modifiers = InternalApiProxy.Modifiers(0)
    assert(!modifiers.isAbstract)
    assert(!modifiers.isFinal)
    assert(!modifiers.isImplicit)
    assert(!modifiers.isLazy)
    assert(!modifiers.isMacro)
    assert(!modifiers.isOverride)
    assert(!modifiers.isSealed)
    assert(!modifiers.isSuperAccessor)
  }

}
