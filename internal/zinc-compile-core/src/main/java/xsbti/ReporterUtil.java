/*
 * Zinc - The incremental compiler for Scala.
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package xsbti;

import sbt.internal.inc.ReporterManager;

import java.io.PrintStream;
import java.io.PrintWriter;

public interface ReporterUtil {
    public static ReporterConfig getDefaultReporterConfig() {
        return ReporterManager.getDefaultReporterConfig();
    }

    public static Reporter getDefault(ReporterConfig config) {
        return ReporterManager.getReporter(System.out, config);
    }

    public static Reporter getReporter(PrintWriter writer, ReporterConfig config) {
        return ReporterManager.getReporter(writer, config);
    }

    public static Reporter getReporter(PrintStream stream, ReporterConfig config) {
        return ReporterManager.getReporter(stream, config);
    }

    public static Reporter getReporter(xsbti.Logger logger, ReporterConfig config) {
        return ReporterManager.getReporter(logger, config);
    }
}
