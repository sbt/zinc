/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.util.Optional;

public class DefaultExternalHooks implements ExternalHooks {
    private Optional<ExternalHooks.Lookup> lookup = Optional.empty();
    private Optional<ClassFileManager> classFileManager = Optional.empty();

    public DefaultExternalHooks(Optional<ExternalHooks.Lookup> lookup, Optional<ClassFileManager> classFileManager) {
        this.lookup = lookup;
        this.classFileManager = classFileManager;
    }

    @Override
    public Optional<ExternalHooks.Lookup> getExternalLookup() {
        return lookup;
    }

    @Override
    public Optional<ClassFileManager> getExternalClassFileManager() {
        return classFileManager;
    }

    @Override
    public ExternalHooks withExternalClassFileManager(ClassFileManager externalClassFileManager) {
        Optional<ClassFileManager> external = Optional.of(externalClassFileManager);
        Optional<ClassFileManager> mixedManager = classFileManager.isPresent()
            ? Optional.of(WrappedClassFileManager.of(classFileManager.get(), external))
            : external;
        return new DefaultExternalHooks(lookup, mixedManager);
    }

    @Override
    public ExternalHooks withExternalLookup(ExternalHooks.Lookup externalLookup) {
        return new DefaultExternalHooks(Optional.of(externalLookup), classFileManager);
    }
}
