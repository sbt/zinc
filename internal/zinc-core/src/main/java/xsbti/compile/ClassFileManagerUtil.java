package xsbti.compile;

import java.util.Optional;

/**
 * Defines utilities to get classfile managers from the types.
 * <p>
 * One can have access to the classfile manager types from {@link IncOptions}.
 */
public final class ClassFileManagerUtil {
    /**
     * Get the default classfile manager implementation for a given classfile manager type.
     *
     * @param classFileManagerType The classfile manager type.
     * @return A classfile manager implementation.
     */
    public static ClassFileManager getDefaultClassFileManager(ClassFileManagerType classFileManagerType) {
        return sbt.internal.inc.ClassFileManager.getDefaultClassFileManager(Optional.of(classFileManagerType));
    }

    /**
     * Get the default classfile manager implementation for a given classfile manager type,
     * extracted from an instance of {@link IncOptions}.
     *
     * @param classFileManagerType The classfile manager type.
     * @return A classfile manager implementation.
     */
    public static ClassFileManager getDefaultClassFileManager(IncOptions incOptions) {
        return sbt.internal.inc.ClassFileManager.getDefaultClassFileManager(incOptions.classfileManagerType());
    }
}
