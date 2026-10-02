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
     * Indicate that the class <code>sourceClass</code> depends on the class
     * <code>onClass</code>, both qualified by the namespace their name lives in.
     *
     * Only classes defined in source files of the current compilation are passed here;
     * for the others see {@link #binaryDependency}.
     *
     * The namespace of <code>sourceClass</code> is what tells <code>object B extends A</code>
     * from <code>trait B extends A</code>, which a source class name alone does not.
     * Zinc reads the namespace of <code>onClass</code> but does not act on it: a class and
     * its companion object currently share one <code>AnalyzedClass</code>>, so there is nothing
     * finer to depend on.
     *
     * @param onClass Source class name being depended on, with its namespace.
     * @param sourceClass Dependent source class name, with its namespace.
     * @param context The kind of dependency established between <code>onClass</code>
     *                and <code>sourceClass</code>.
     *
     * @see xsbti.api.DependencyContext
     */
    void classDependency(ClassRef onClass,
                         ClassRef sourceClass,
                         DependencyContext context);

    /**
     * Indicate that the class <code>fromClass</code> depends on a class named
     * <code>onBinaryClassName</code> coming from class file or jar
     * <code>onBinaryEntry</code>.
     *
     * Only <code>fromClass</code> carries a namespace. A binary name already encodes
     * the distinction, as <code>A</code> for a class and <code>A$</code> for an object.
     *
     * @param onBinaryEntry The jar or the class file the compiler knows
     *                      <code>onBinaryClassName</code> comes from.
     * @param onBinaryClassName Binary name being depended on, in the JVM format
     *                          described in section 13.1 of the Java Language
     *                          Specification. Inner classes are written with '$'.
     * @param fromClass Dependent source class name, with its namespace.
     * @param fromSourceFile Source file where <code>fromClass</code> is defined.
     * @param context The kind of dependency established between
     *                <code>onBinaryClassName</code> and <code>fromClass</code>.
     *
     * @see xsbti.api.DependencyContext
     */
    void binaryDependency(Path onBinaryEntry,
                          String onBinaryClassName,
                          ClassRef fromClass,
                          VirtualFileRef fromSourceFile,
                          DependencyContext context);

    /**
     * Register the use of a <code>name</code> from a given source class name, together
     * with where the referenced member is defined: in a class (Type), in an object (Term),
     * or either.
     *
     * A class and its companion object share a source class name, so a member defined in
     * both is otherwise one name. Pass both kinds whenever the use does not say which one
     * it refers to, as for an import selector, an inherited member that either side can
     * override, or a structural member.
     *
     * @param className The source class name that uses <code>name</code>.
     * @param name The source name used in <code>className</code>.
     * @param ownerKinds Which side of a companion pair declares the member referred to.
     * @param useScopes Scopes (e.g. patmat, implicit) where name is used in
     *                  <code>className</code>.
     */
    void usedName(String className,
                  String name,
                  EnumSet<NameKind> ownerKinds,
                  EnumSet<UseScope> useScopes);
}
