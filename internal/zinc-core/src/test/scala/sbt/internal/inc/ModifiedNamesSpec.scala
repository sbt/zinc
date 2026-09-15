/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt.internal.inc

import xsbti.{ NameKind, UseScope }
import xsbti.api.NameHash

class ModifiedNamesSpec extends UnitSpec {
  private def hash(ownerKind: NameKind, value: Int) =
    NameHash.of("x", UseScope.Default, value, ownerKind)

  private def used(ownerKinds: NameKind*) =
    UsedName("x", Seq(UseScope.Default), ownerKinds)

  // class A { def x: Int }; object A { def x: String }, and only the class's x changes
  private val classXChanged = ModifiedNames.compareTwoNameHashes(
    Array(hash(NameKind.Type, 1), hash(NameKind.Term, 2)),
    Array(hash(NameKind.Type, 3), hash(NameKind.Term, 2))
  )

  "Modified names" should "tell a class member from an object member of the same name" in {
    assert(classXChanged.isModified(used(NameKind.Type)))
    assert(!classXChanged.isModified(used(NameKind.Term)))
  }

  it should "treat a used name defined in either as affected by both" in {
    assert(classXChanged.isModified(used(NameKind.Type, NameKind.Term)))
  }
}
