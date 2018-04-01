/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile.analysis;

import xsbti.Problem;
import xsbti.semanticdb3.SymbolOccurrence;

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
     * Returns the occurred symbols found in this compilation unit.
     *
     * @return occurred symbols
     */
    public SymbolOccurrence[] getSymbolOccurrences();


    /**
     * Return the name of the symbol at the specified position.
     *
     *
     * @param line The line position in source.
     * @param character The character position in source.
     * @return The full name of a symbol.
     */
    public Optional<String> getSymbolNameByPosition(int line, int character);

    /**
     * Return the <code>SymbolOccurrence<code/> where the symbol is defined.
     *
     * @param symbolName The name of a symbol.
     * @return The position in source.
     */
    public Set<SymbolOccurrence> getSymbolDefinition(String symbolName);

}

