#!/usr/bin/env bash
set -eu
set -o nounset
SCALA_VERSION="$1"

sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=256M \
  -J-Xmx3046M -J-Xms3046M -J-server \
  +mimaReportBinaryIssues \
  scalafmt::test \
  test:scalafmt::test \
  whitesourceCheckPolicies \
  compilerInterfaceJava6Compat/compile \
  zincRoot/test:compile \
  bloopScripted/compile \
  crossTestBridges \
  zincRoot/test \
  zincRoot/scripted
