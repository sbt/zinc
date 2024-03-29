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

package xsbti.compile.analysis;

import java.nio.file.Path;

public final class ReadWriteMappers {
    private ReadMapper readMapper;
    private WriteMapper writeMapper;

    public ReadWriteMappers(ReadMapper readMapper, WriteMapper writeMapper) {
        this.readMapper = readMapper;
        this.writeMapper = writeMapper;
    }

    public static ReadWriteMappers getMachineIndependentMappers(Path path) {
        ReadMapper readMapper = ReadMapper.getMachineIndependentMapper(path);
        WriteMapper writeMapper = WriteMapper.getMachineIndependentMapper(path);
        return new ReadWriteMappers(readMapper, writeMapper);
    }

    public static ReadWriteMappers getEmptyMappers() {
        ReadMapper readMapper = ReadMapper.getEmptyMapper();
        WriteMapper writeMapper = WriteMapper.getEmptyMapper();
        return new ReadWriteMappers(readMapper, writeMapper);
    }

    public ReadMapper getReadMapper() {
        return readMapper;
    }

    public WriteMapper getWriteMapper() {
        return writeMapper;
    }
}
