#!/usr/bin/env bash
set -eu
set -o nounset

PROJECT_ROOT="zincRoot"
sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=512M \
  -J-Xms1024M -J-Xmx2048M -J-server \
  "$PROJECT_ROOT/mimaReportBinaryIssues" \
  scalafmtCheckAll \
  scalafmtSbtCheck \
  whitesourceCheckPolicies \
  "$PROJECT_ROOT/test:compile" \
  crossTestBridges \
  "$PROJECT_ROOT/test" \
  scripted
