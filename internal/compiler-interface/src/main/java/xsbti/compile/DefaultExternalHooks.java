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

import java.util.Optional;

public class DefaultExternalHooks implements ExternalHooks {
    private Optional<ExternalHooks.Lookup> lookup = Optional.empty();
    private Optional<ClassFileManager> classFileManager = Optional.empty();
    private GetProvenance getProvenance = NoProvenance.INSTANCE;

    public DefaultExternalHooks(
            Optional<ExternalHooks.Lookup> lookup,
            Optional<ClassFileManager> classFileManager,
            GetProvenance getProvenance
    ) {
        this.lookup = lookup;
        this.classFileManager = classFileManager;
        this.getProvenance = getProvenance;
    }

    public DefaultExternalHooks(Optional<ExternalHooks.Lookup> lookup, Optional<ClassFileManager> classFileManager) {
        this(lookup, classFileManager, NoProvenance.INSTANCE);
    }

    @Override
    public Optional<ExternalHooks.Lookup> getExternalLookup() {
        return lookup;
    }

    @Override
    public Optional<ClassFileManager> getExternalClassFileManager() {
        return classFileManager;
    }

    @Override public GetProvenance getProvenance() { return getProvenance; }

    @Override
    public ExternalHooks withExternalClassFileManager(ClassFileManager externalClassFileManager) {
        Optional<ClassFileManager> external = Optional.of(externalClassFileManager);
        Optional<ClassFileManager> mixedManager = classFileManager.isPresent()
            ? Optional.of(WrappedClassFileManager.of(classFileManager.get(), external))
            : external;
        return new DefaultExternalHooks(lookup, mixedManager, getProvenance);
    }

    @Override
    public ExternalHooks withExternalLookup(ExternalHooks.Lookup externalLookup) {
        Optional<Lookup> externalLookup1 = Optional.of(externalLookup);
        return new DefaultExternalHooks(externalLookup1, classFileManager, getProvenance);
    }

    @Override
    public ExternalHooks withGetProvenance(GetProvenance getProvenance) {
        return new DefaultExternalHooks(lookup, classFileManager, getProvenance);
    }
}
