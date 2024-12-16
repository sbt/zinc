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

class NestedJavaClasses {
    public NestedJavaClasses nest() {
        return new NestedJavaClasses() {
            @Override
            public NestedJavaClasses nest() {
                return new NestedJavaClasses() {
                    @Override
                    public NestedJavaClasses nest() {
                        throw new RuntimeException("Way too deep!");
                    }
                };
            }
        };
    }
}