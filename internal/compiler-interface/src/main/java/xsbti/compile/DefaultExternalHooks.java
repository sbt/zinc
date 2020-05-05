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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    @Override
    public Map<String, Object> extraHooks() {
        return Collections.unmodifiableMap(new HashMap<>());
    }
}
