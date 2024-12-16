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

import java.rmi.RMISecurityException;

public class Test {
    public NotFound foo() { return 5; }

    public String warning() {
        throw new RMISecurityException("O NOES");
    }
}

class C {
    class D {}
    void test() {
        D.this.toString();
    }
}
