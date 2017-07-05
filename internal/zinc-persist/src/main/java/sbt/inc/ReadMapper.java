package sbt.inc;

import sbt.internal.inc.mappers.RelativeReadMapper;
import xsbti.compile.MiniSetup;
import xsbti.compile.analysis.Stamp;

import java.io.File;
import java.nio.file.Path;

/**
 * Defines a reader-only mapper interface that is used by Zinc before read
 * the contents of the analysis files from the persistent storage.
 *
 * This interface is useful to make the analysis file machine-independent and
 * allow third parties to distribute these files around.
 */
public interface ReadMapper extends GenericMapper {
    public static ReadMapper getMachineIndependentMapper(Path projectRootPath) {
        return new RelativeReadMapper(projectRootPath);
    }

    public static ReadMapper getEmptyMapper() {
        return new ReadMapper() {
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
