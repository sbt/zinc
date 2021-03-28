package xsbti.compile;

import java.nio.file.Path;

public class AuxiliaryClassFileExtension implements AuxiliaryClassFiles {
    private final String dotExtension;

    public AuxiliaryClassFileExtension(String extension) {
        this.dotExtension = "." + extension;
    }

    @Override
    public Path[] associatedFiles(Path classFile) {
        // not all class files must have a corresponding auxiliary file
        // we strongly assume that ClassFileManager is forgiving on files that do not exist.
        String fileName = classFile.getFileName().toString();
        if (fileName.endsWith(".class")) {
            String prefix = fileName.substring(0, fileName.length() - 6);
            Path auxiliaryFile = classFile.resolveSibling(prefix + dotExtension);
            return new Path[] { auxiliaryFile };
        } else  {
            return new Path[0];
        }
    }
}
