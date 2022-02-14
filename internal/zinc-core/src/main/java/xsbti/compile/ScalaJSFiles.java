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

/**
 * Can be added to `IncOptions.auxiliaryClassFiles` so that the sjsir files
 * produced by the Scala.js compiler are managed by the ClassFileManager.
 */
public class ScalaJSFiles extends AuxiliaryClassFileExtension {
    private static final ScalaJSFiles _instance = new ScalaJSFiles();

    public static ScalaJSFiles instance() {
        return _instance;
    }

    private ScalaJSFiles() {
        super("sjsir");
    }
}