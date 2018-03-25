/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile.analysis;

import xsbti.Problem;

import java.util.Optional;
import java.util.Set;

/**
 * Defines the compiler information for a given compilation unit (source file).
 */
public interface SourceInfo {
    /**
     * Returns the reported problems by the Java or Scala compiler.
     * <p>
     * Note that a reported problem is a problem that has been shown to the end user.
     *
     * @return The compiler reported problems.
     */
    public Problem[] getReportedProblems();

    /**
     * Returns the unreported problems by the Java or Scala compiler.
     * <p>
     * Note that an unreported problem is a problem that has not been shown to the end user.
     *
     * @return The compiler reported problems.
     */
    public Problem[] getUnreportedProblems();

    /**
     * Returns the main classes found in this compilation unit.
     *
     * @return The full name of the main classes, like "foo.bar.Main"
     */
    public String[] getMainClasses();


    /**
     * Returns the used names found in this compilation unit.
     *
     * @return used names and its positions
     */
    public NamePosition[] getUsedNamePositions();

    /**
     * Returns the defined names found in this compilation unit.
     *
     * @return defined names and its positions
     */
    public NamePosition[] getDefinedNamePositions();

    /**
     * Return the full name of the symbol at the specified position.
     *
     * @param line The line position in source.
     * @param column The column position in source.
     * @return The full name of a symbol.
     */
    public Optional<String> getFullNameByPosition(int line, int column);

    /**
     * Return the position where the symbol is defined.
     *
     * @param fullName The full name of a symbol.
     * @return The position in source.
     */
    public Set<NamePosition> getPositionByFullName(String fullName);

}

