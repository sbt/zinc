package xsbti;

import xsbti.api.DependencyContext;

import java.io.File;

public interface DependencyCallback {
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
   * named <code>onBinaryClassName</code> coming from class file or jar
   * <code>onBinaryEntry</code>.
   *
   * @param onBinaryEntry A binary entry represents either the jar or the
   *                      concrete class file from which the Scala compiler
   *                      knows that <code>onBinaryClassName</code> comes from.
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
  void binaryDependency(File onBinaryEntry,
                        String onBinaryClassName,
                        String fromClassName,
                        File fromSourceFile,
                        DependencyContext context);

  /**
   * Communicate to the callback that the dependency phase has finished.
   *
   * For instance, you can use this method it to wait on asynchronous tasks.
   */
  void dependencyPhaseCompleted();
}
