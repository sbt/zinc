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
import java.nio.file.Path;
import java.util.ArrayList;
import xsbti.VirtualFile;

/**
 * Represent the interface to manage the generated class files by the
 * Scala or Java compilers. The class file manager is responsible for
 * providing operations to users to allow them to have a fine-grained
 * control over the generated class files and how they are generated/deleted.
 *
 * This class is meant to be used once per compilation run.
 */
public interface ClassFileManager {
    /**
     * Handler of classes that deletes them prior to every compilation step.
     *
     * @param classes The generated class files must not exist if the method
     *                returns normally, as well as any empty ancestor
     *                directories of deleted files.
     */
    default void delete(VirtualFile[] classes) {
      delete(ToFileArray.convert(classes));
    }

    @Deprecated
    /** Called once per compilation step with the class files generated during that step. */
    /**
     * Handler of classes that decides where certain class files should be
     * stored after every compilation step.
     *
     * This method is called once per compilation run with the class
     * files generated by that concrete run.
     *
     * @param classes The generated class files by the immediate compilation run.
     */
    void delete(File[] classes);


    /** Called once per compilation step with the class files generated during that step. */
    /**
     * Handler of classes that decides where certain class files should be
     * stored after every compilation step.
     *
     * This method is called once per compilation run with the class
     * files generated by that concrete run.
     *
     * @param classes The generated class files by the immediate compilation run.
     */
    default void generated(VirtualFile[] classes) {
      generated(ToFileArray.convert(classes));
    }

    @Deprecated
    /** Called once per compilation step with the class files generated during that step. */
    /**
     * Handler of classes that decides where certain class files should be
     * stored after every compilation step.
     *
     * This method is called once per compilation run with the class
     * files generated by that concrete run.
     *
     * @param classes The generated class files by the immediate compilation run.
     */
    void generated(File[] classes);

    /** Called once at the end of the whole compilation run, with `success`
     * indicating whether compilation succeeded (true) or not (false). */
    /**
     * Informs the class file manager whether the compilation run has succeeded.
     *
     * If it has not succeeded, the class file manager will handle the current
     * generated and the previous class files as per the underlying algorithm.
     *
     * @param success Whether the compilation run has succeeded or not.
     */
    void complete(boolean success);

}

class ToFileArray {
  static File[] convert(VirtualFile[] classes) {
    final ArrayList<File> files = new ArrayList<>();
    for (VirtualFile c: classes) {
      try {
        final Object path = c.getClass().getDeclaredMethod("toPath").invoke(c);
        if (path instanceof Path) files.add(((Path) path).toFile());
      } catch (Exception e) {
        // ignore
      }
    }
    final File[] result = new File[files.size()];
    for (int i = 0; i < files.size(); ++i) {
      result[i] = files.get(i);
    }
    return result;
  }
}
