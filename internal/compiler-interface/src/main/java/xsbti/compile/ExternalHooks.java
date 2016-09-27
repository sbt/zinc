package xsbti.compile;

import java.io.File;

/**
 * Created by krzysztr on 02/08/2016.
 */
public interface ExternalHooks {
    public static interface Lookup {}

    /**
     * During an incremental compilation run, a ClassfileManager deletes class files and is notified of generated class files.
     * A ClassfileManager can be used only once.
     */
    public static interface ClassFileManager {
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

    Lookup externalLookup();

    ClassFileManager externalClassFileManager();
}
