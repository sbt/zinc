/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;

/**
 * During an incremental compilation run, a ClassfileManager deletes class files and is notified of generated class files.
 * A ClassfileManager can be used only once.
 */
public interface ClassFileManager {
    /**
     * Called once per compilation step with the class files to delete prior to that step's compilation.
     * The files in `classes` must not exist if this method returns normally.
     * Any empty ancestor directories of deleted files must not exist either.
     */
    void delete(File[] classes);

    /** Called once per compilation step with the class files generated during that step. */
    void generated(File[] classes);

    /** Called once at the end of the whole compilation run, with `success` indicating whether compilation succeeded (true) or not (false). */
    void complete(boolean success);
}
