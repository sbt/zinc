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

import java.io.File;
import java.util.Optional;
import xsbti.VirtualFile;

/**
 * Defines a classfile manager that composes the operation of two classfile manager,
 * one being the internal classfile manager (the one used by the compiler) and the
 * other one being the external classfile manager (a customizable, build tool-defined
 * class file manager to control which class files should be notified/removed/generated
 * aside from the ones covered by the internal classfile manager).
 */
public class WrappedClassFileManager implements ClassFileManager {
  private ClassFileManager internal;
  private Optional<ClassFileManager> external;

  public static WrappedClassFileManager of(ClassFileManager internal, Optional<ClassFileManager> external) {
      return new WrappedClassFileManager(internal, external);
  }

  protected WrappedClassFileManager(ClassFileManager internal,
                                    Optional<ClassFileManager> external) {
    this.internal = internal;
    this.external = external;
  }

  @Override
  public void delete(VirtualFile[] classes) {
    // Avoid Java 8 syntax to accommodate Scala 2.10
    if (external.isPresent()) {
      external.get().delete(classes);
    }
    internal.delete(classes);
  }

  @Override
  @Deprecated
  public void delete(File[] classes) {
    // Avoid Java 8 syntax to accommodate Scala 2.10
    if (external.isPresent()) {
      external.get().delete(classes);
    }
    internal.delete(classes);
  }


  @Override
  public void complete(boolean success) {
    // Avoid Java 8 syntax to accommodate Scala 2.10
    if (external.isPresent()) {
      external.get().complete(success);
    }
    internal.complete(success);
  }

  @Override
  public void generated(VirtualFile[] classes) {
    // Avoid Java 8 syntax to accommodate Scala 2.10
    if (external.isPresent()) {
      external.get().generated(classes);
    }
    internal.generated(classes);
  }

  @Override
  @Deprecated
  public void generated(File[] classes) {
    // Avoid Java 8 syntax to accommodate Scala 2.10
    if (external.isPresent()) {
      external.get().generated(classes);
    }
    internal.generated(classes);
  }
}
