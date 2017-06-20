package xsbti;

import sbt.internal.inc.LoggerReporter;
import sbt.internal.inc.ReporterManager;
import sbt.internal.util.ManagedLogger;
import sbt.util.Level$;

import java.util.function.Function;

public interface ReporterUtil {
    public static ReporterConfig getDefaultReporterConfig() {
        return ReporterManager.getDefaultReporterConfig();
    }

    public static Reporter getDefault(ReporterConfig config) {
        return ReporterManager.getReporter(System.out, config);
    }
}
