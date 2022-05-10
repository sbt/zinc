#!/usr/bin/env bash
set -eu
set -o nounset

sbt -Dfile.encoding=UTF-8 \
  mimaReportBinaryIssues \
  Test/compile \
  doc \
  crossTestBridges \
  test \
  scripted
