/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti;

import xsbti.api.DependencyContext;
import java.io.File;

public interface AnalysisCallback {
    /**
     * Set the source file mapped to a concrete {@link AnalysisCallback}.
     * @param source Source file mapped to this instance of {@link AnalysisCallback}.
     */
    void startSource(File source);

    /**
     * Indicate that the class <code>sourceClassName</code> depends on the
     * class <code>onClassName</code>.
     *
     * Note that only classes defined in source files included in the current
     * compilation will passed to this method. Dependencies on classes generated
     * by sources not in the current compilation will be passed as binary
     * dependencies to the `binaryDependency` method.
     *
     * @param onClassName Class name being depended on.
     * @param sourceClassName Dependent class name.
     * @param context The kind of dependency established between
     *                <code>onClassName</code> and <code>sourceClassName</code>.
     *
     * @see xsbti.api.DependencyContext
     */
    void classDependency(String onClassName,
                         String sourceClassName,
                         DependencyContext context);

    /**
     * Indicate that the class <code>fromClassName</code> depends on a class
     * named <code>onBinaryClassName</code> coming from class or jar file
     * <code>binary</code>.
     *
     * @param onBinary Binary name being depended on.
     *                 Binary name with JVM-like representation. Inner classes
     *                 are represented with '$'. For more information on the
     *                 binary name format, check section 13.1 of the Java
     *                 Language Specification.
     * @param onBinaryClassName Dependent binary name.
     *                 Binary name with JVM-like representation. Inner classes
     *                 are represented with '$'. For more information on the
     *                 binary name format, check section 13.1 of the Java
     *                 Language Specification.
     * @param fromClassName Represent the class file name where
     *                 <code>onBinaryClassName</code> is defined.
     *                 Binary name with JVM-like representation. Inner classes
     *                 are represented with '$'. For more information on the
     *                 binary name format, check section 13.1 of the Java
     *                 Language Specification.
     * @param fromSourceFile Source file where <code>onBinaryClassName</code>
     *                       is defined.
     * @param context The kind of dependency established between
     *                <code>onClassName</code> and <code>sourceClassName</code>.
     *
     * @see xsbti.api.DependencyContext for more information on the context.
     */
    void binaryDependency(File onBinary,
                          String onBinaryClassName,
                          String fromClassName,
                          File fromSourceFile,
                          DependencyContext context);

    /**
     * Map the source class name (<code>srcClassName</code>) of a top-level
     * Scala class coming from a given source file to a binary class name
     * (<code>binaryClassName</code>) coming from a given class file.
     *
     * This relation indicates that <code>classFile</code> is the product of
     * compilation from <code>source</code>.
     *
     * @param source File where <code>srcClassName</code> is defined.
     * @param classFile File where <code>binaryClassName</code> is defined. This
     *                  class file is the product of <code>source</code>.
     * @param binaryClassName Binary name with JVM-like representation. Inner
     *                        classes are represented with '$'. For more
     *                        information on the binary name format, check
     *                        section 13.1 of the Java Language Specification.
     * @param srcClassName Class name as defined in <code>source</code>.
     */
    void generatedNonLocalClass(File source,
                                File classFile,
                                String binaryClassName,
                                String srcClassName);

    /**
     * Map the product relation between <code>classFile</code> and
     * <code>source</code> to indicate that <code>classFile</code> is the
     * product of compilation from <code>source</code>.
     *
     * @param source File that produced <code>classFile</code>.
     * @param classFile File product of compilation of <code>source</code>.
     */
    void generatedLocalClass(File source, File classFile);

    /**
     * Register a public API entry coming from a given source file.
     *
     * @param sourceFile Source file where <code>classApi</code> comes from.
     * @param classApi The extracted public class API.
     */
    void api(File sourceFile, xsbti.api.ClassLike classApi);

    /**
     * Register the use of a <code>name</code> from a given source class name.
     *
     * @param className The source class name that uses <code>name</code>.
     * @param name The source name used in <code>className</code>.
     */
    void usedName(String className, String name);

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
     */
    void problem(String what,
                 Position pos,
                 String msg,
                 Severity severity,
                 boolean reported);

    /**
     * Determine whether method calls through this interface should be
     * interpreted to use the name-hashing algorithm in the given compiler run.
     *
     *
     * In particular, it indicates whether member references, inheritance
     * dependencies and local inherited dependencies should be extracted.
     *
     * @see xsbti.api.DependencyContext for more information on the sort of
     * dependency that can be registered.
     *
     * The implementation of this method is meant to be free of side-effects.
     * It's added to {@link AnalysisCallback} because it indicates how other
     * callback calls should be interpreted by its implementations and clients.
     *
     * This method is an implementation detail and can be removed at
     * any point without any deprecation. Do not depend on it.
     */
    boolean nameHashing();

    /**
     * Communicate to the callback that the dependency phase has finished.
     *
     * For instance, you can use this method it to wait on asynchronous tasks.
     */
    void dependencyPhaseCompleted();

    /**
     * Communicate to the callback that the API phase has finished.
     *
     * For instance, you can use this method it to wait on asynchronous tasks.
     */
    void apiPhaseCompleted();

    /**
     * Return whether incremental compilation is enabled or not.
     *
     * This method is useful to know whether the incremental compilation
     * phase defined by <code>xsbt-analyzer</code> should be added.
     */
    boolean enabled();
}