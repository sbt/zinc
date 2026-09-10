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

package xsbti;

import xsbti.api.DependencyContext;

import java.nio.file.Path;
import java.util.EnumSet;

/**
 * Extension to {@link AnalysisCallback3}.
 *
 * Dependency edges and used names are keyed by source class name, which a class
 * and its companion object share. These overloads add the namespace, so that Zinc
 * can tell the two apart. A bridge that only calls the inherited overloads keeps
 * the previous behaviour, where they are conflated.
 */
public interface AnalysisCallback4 extends AnalysisCallback3 {
    /**
     * Same as {@link AnalysisCallback#classDependency}, with both endpoints qualified
     * by the namespace their name lives in.
     */
    void classDependency(ClassRef onClass,
                         ClassRef sourceClass,
                         DependencyContext context);

    /**
     * Same as {@link AnalysisCallback#binaryDependency}, with the depending class
     * qualified by the namespace its name lives in.
     */
    void binaryDependency(Path onBinaryEntry,
                          String onBinaryClassName,
                          ClassRef fromClass,
                          VirtualFileRef fromSourceFile,
                          DependencyContext context);

    /**
     * Same as {@link AnalysisCallback#usedName}, with whether <code>name</code> refers to a
     * member of a class (Type) or of an object (Term). A class and its companion object share
     * a class name, so a member defined in both is otherwise one name. Pass both kinds unless
     * the use tells which one it refers to.
     */
    void usedName(String className,
                  String name,
                  EnumSet<NameKind> ownerKinds,
                  EnumSet<UseScope> useScopes);
}
