#!/usr/bin/env bash
set -eu
set -o nounset
SCALA_VERSION="$1"

# Exemplifies how the build should be used by user
# The following script makes sure to:
#  1. Checking binary compatibility
#  2. Checking formatting and headers 
#  3. Testing the compiler bridge for all versions
#  4. Setting up the scala version specified by the argument
#  5. Running tests for all the projects and then scripted

sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=256M \
  -J-Xmx3046M -J-Xms3046M -J-server \
  +mimaReportBinaryIssues \
  scalafmt::test \
  test:scalafmt::test \
  headerCheck \
  test:headerCheck \
  ++2.10.6 \
  compilerInterface/compile \
  zincRoot/test:compile \
  +compilerBridge/cachedPublishLocal \
  +compilerBridge/test \
  "++$SCALA_VERSION" \
  zincRoot/test \
  zincRoot/scripted
