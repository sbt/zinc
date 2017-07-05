package sbt.inc;

import sbt.internal.inc.mappers.RelativeWriteMapper;

import java.nio.file.Path;

/**
 * Defines a writer-only mapper interface that is used by Zinc before writing
 * the contents of the analysis files to the persistent storage.
 *
 * This interface is useful to make the analysis file machine-independent and
 * allow third parties to distribute these files around.
 */
public interface WriteMapper extends GenericMapper {
    public static WriteMapper getMachineIndependentMapper(Path projectRootPath) {
        return new RelativeWriteMapper(projectRootPath);
    }
}
