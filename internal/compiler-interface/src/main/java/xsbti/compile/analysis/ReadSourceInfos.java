package xsbti.compile.analysis;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

/**
 * Defines a read-only interface to get compiler information mapped to a source file.
 */
public interface ReadSourceInfos {
    /**
     * Returns the {@link SourceInfo sourceInfo} associated with a source file.
     *
     * @param sourceFile The source info associated with a source file.
     * @return A {@link SourceInfo sourceInfo}.
     */
    public SourceInfo get(File sourceFile);

    /**
     * Returns a map of all source files with their corresponding source infos.
     *
     * @return A map of source files to source infos.
     * @see SourceInfo
     */
    public Map<File, SourceInfo> getAllSourceInfos();
}
