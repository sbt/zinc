package xsbti.compile;

import java.nio.file.Path;

/**
 * Interface that associates a class file with other corresponding produced
 * files, for instance `.tasty` files or `.sjsir` files.
 *
 * This class is meant to be used inside the [[ClassFileManager]] to manage
 * auxiliary files.
 *
 * ClassFileManagers are forgiving on auxiliary files that do not exist.
 */
public interface AuxiliaryClassFiles {
    Path[] associatedFiles(Path classFile);
}
