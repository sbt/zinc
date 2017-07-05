package sbt.inc;

import sbt.internal.inc.mappers.RelativeWriteMapper;
import xsbti.compile.MiniSetup;
import xsbti.compile.analysis.Stamp;

import java.io.File;
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

    public static WriteMapper getEmptyMapper() {
        return new WriteMapper() {
            @Override
            public File mapSourceFile(File sourceFile) {
                return sourceFile;
            }

            @Override
            public File mapBinaryFile(File binaryFile) {
                return binaryFile;
            }

            @Override
            public File mapProductFile(File productFile) {
                return productFile;
            }

            @Override
            public File mapOutputDir(File outputDir) {
                return outputDir;
            }

            @Override
            public File mapSourceDir(File sourceDir) {
                return sourceDir;
            }

            @Override
            public File mapClasspathEntry(File classpathEntry) {
                return classpathEntry;
            }

            @Override
            public String mapJavacOption(String javacOption) {
                return javacOption;
            }

            @Override
            public String mapScalacOption(String scalacOption) {
                return scalacOption;
            }

            @Override
            public Stamp mapBinaryStamp(File file, Stamp binaryStamp) {
                return binaryStamp;
            }

            @Override
            public Stamp mapSourceStamp(File file, Stamp sourceStamp) {
                return sourceStamp;
            }

            @Override
            public Stamp mapProductStamp(File file, Stamp productStamp) {
                return productStamp;
            }

            @Override
            public MiniSetup mapMiniSetup(MiniSetup miniSetup) {
                return miniSetup;
            }
        };
    }
}
