/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;
import java.util.Optional;
import java.util.Set;

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
        Optional<ClassFileManager> currentManager = this.getExternalClassFileManager();
        Optional<ClassFileManager> mixedManager = currentManager;
        if (currentManager.isPresent()) {
            Optional<ClassFileManager> external = Optional.of(externalClassFileManager);
            mixedManager = Optional.of(WrappedClassFileManager.of(currentManager.get(), external));
        }
        return new DefaultExternalHooks(this.getExternalLookup(), mixedManager);
    }

    @Override
    public ExternalHooks withExternalLookup(ExternalHooks.Lookup externalLookup) {
        return new DefaultExternalHooks(Optional.of(externalLookup), this.getExternalClassFileManager());
    }
}
