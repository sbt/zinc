/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package sbt.inc;

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
