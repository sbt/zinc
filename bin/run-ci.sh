#!/usr/bin/env bash
set -eu
set -o nounset

PROJECT_ROOT="zincRoot"
sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=512M \
  -J-Xms1024M -J-Xmx4096M -J-server \
  "$PROJECT_ROOT/mimaReportBinaryIssues" \
  scalafmt::test \
  test:scalafmt::test \
  whitesourceCheckPolicies \
  "$PROJECT_ROOT/test:compile" \
  crossTestBridges \
  "publishBridges" \
  "$PROJECT_ROOT/test"