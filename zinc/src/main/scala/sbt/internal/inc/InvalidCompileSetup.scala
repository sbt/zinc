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

package sbt
package internal
package inc

/**
 * Signals a build setup that Zinc cannot honour. Unlike [[CompileFailed]] this says nothing
 * about the sources, so it is not reported as a compilation error.
 */
final class InvalidCompileSetup(message: String) extends RuntimeException(message)
