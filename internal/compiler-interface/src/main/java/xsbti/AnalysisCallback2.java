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

import java.util.List;
import java.util.Optional;

/**
 * Extension to AnalsysisCallback.
 * This is a compatibility layer created for Scala 3.x compilers.
 * Even though compiler-interface is NOT intended to be a public API,
 * Scala 3 compiler publishes their own compiler bridge against Zinc de jour.
 * By having this interface, they can test if `callback` supports AnalysisCallback2.
 */
public interface AnalysisCallback2 extends AnalysisCallback {
    /**
     * Register a compilation problem.
     *
     * This error may have already been logged or not. Unreported problems may
     * happen because the reporting option was not enabled via command-line.
     *
     * @param what The headline of the error.
     * @param pos At a given source position.
     * @param msg The in-depth description of the error.
     * @param severity The severity of the error reported.
     * @param reported Flag that confirms whether this error was reported or not.
     * @param rendered If present, the string shown to the user when displaying this Problem.
     * @param diagnosticCode The unique code attached to the diagnostic being reported.
     * @param diagnosticRelatedInformation The possible releated information for the diagnostic being reported.
     * @param actions Actions (aka quick fixes) that are able to either fix or address the issue that is causing this problem.
     */
    void problem2(String what,
                 Position pos,
                 String msg,
                 Severity severity,
                 boolean reported,
                 Optional<String> rendered,
                 Optional<DiagnosticCode> diagnosticCode,
                 List<DiagnosticRelatedInformation> diagnosticRelatedInformation,
                 List<Action> actions);
}
