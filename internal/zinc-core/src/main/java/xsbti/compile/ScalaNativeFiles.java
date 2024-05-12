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

/**
 * Can be added to `IncOptions.auxiliaryClassFiles` so that the nir files
 * produced by the Scala Native compiler are managed by the ClassFileManager.
 */
public class ScalaNativeFiles extends AuxiliaryClassFileExtension {
    private static final ScalaNativeFiles _instance = new ScalaNativeFiles();

    public static ScalaNativeFiles instance() {
        return _instance;
    }

    private ScalaNativeFiles() {
        super("nir");
    }
}