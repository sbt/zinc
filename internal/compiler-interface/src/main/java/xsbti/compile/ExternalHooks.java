/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

/**
 * Created by krzysztr on 02/08/2016.
 */
public interface ExternalHooks {
    public static interface Lookup {}

    Lookup externalLookup();

    ClassFileManager externalClassFileManager();
}
