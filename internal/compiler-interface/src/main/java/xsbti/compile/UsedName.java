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

package xsbti.compile;

import xsbti.NameKind;
import xsbti.UseScope;

public interface UsedName {
    String getName();
    java.util.EnumSet<UseScope> getScopes();

    /**
     * Whether the used name refers to a member of a class (Type) or of an object (Term);
     * both when the use does not tell.
     */
    default java.util.EnumSet<NameKind> getOwnerKinds() {
        return java.util.EnumSet.allOf(NameKind.class);
    }
}

