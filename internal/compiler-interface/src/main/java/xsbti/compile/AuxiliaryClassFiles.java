/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Lightbend, Inc. and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

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
