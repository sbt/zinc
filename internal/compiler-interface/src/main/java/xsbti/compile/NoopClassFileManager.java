package xsbti.compile;

import java.io.File;

public class NoopClassFileManager implements ClassFileManager {
    @Override
    public void delete(File[] classes) {
    }

    @Override
    public void generated(File[] classes) {
    }

    @Override
    public void complete(boolean success) {
    }
}
