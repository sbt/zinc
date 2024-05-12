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

package xsbti.compile.analysis;

import xsbti.VirtualFileRef;
import xsbti.compile.MiniSetup;

import java.nio.file.Path;

/**
 * Defines a generic interface to map the values of the analysis file 1-to-1.
 */
public interface GenericMapper {
    /**
     * @param sourceFile A source file to be compiled.
     * @return A modified source file.
     */
    VirtualFileRef mapSourceFile(VirtualFileRef sourceFile);

    /**
     * @param binaryFile A binary dependency of the sources to be compiled.
     * @return A modified binary file.
     */
    VirtualFileRef mapBinaryFile(VirtualFileRef binaryFile);

    /**
     * @param productFile A product file (class file) produced by the compiler.
     * @return A modified product file.
     */
    VirtualFileRef mapProductFile(VirtualFileRef productFile);

    /**
     * @param outputDir The output dir where the compiler will output the products.
     * @return A modified output dir.
     */
    Path mapOutputDir(Path outputDir);

    /**
     * @param sourceDir The source dir where the compiler will look for the sources.
     * @return A modified source dir.
     */
    Path mapSourceDir(Path sourceDir);

    /**
     * @param classpathEntry The classpath entry to be passed to the compiler.
     * @return A modified classpath entry.
     */
    Path mapClasspathEntry(Path classpathEntry);

    /**
     * @param javacOption An option to be passed to the Java compiler.
     * @return A compiler option.
     */
    String mapJavacOption(String javacOption);

    /**
     * @param scalacOption An options to be passed to the Scala compiler.
     * @return A compiler option.
     */
    String mapScalacOption(String scalacOption);

    /**
     * @param file The owner of the stamp.
     * @param binaryStamp A stamp associated to a binary file.
     * @return A transformed stamp.
     */
    Stamp mapBinaryStamp(VirtualFileRef file, Stamp binaryStamp);

    /**
     * @param file The owner of the stamp.
     * @param sourceStamp A stamp associated to a source file.
     * @return A transformed stamp.
     */
    Stamp mapSourceStamp(VirtualFileRef file, Stamp sourceStamp);

    /**
     * @param file The owner of the stamp.
     * @param productStamp A stamp associated to a product file.
     * @return A transformed stamp.
     */
    Stamp mapProductStamp(VirtualFileRef file, Stamp productStamp);

    /**
     * @param miniSetup The simple compile setup that is serialized in the analysis file.
     * @return A transformed mini setup.
     */
    MiniSetup mapMiniSetup(MiniSetup miniSetup);
}
