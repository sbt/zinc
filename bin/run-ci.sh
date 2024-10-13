#!/usr/bin/env bash
set -eu
set -o nounset

sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=512M \
  -J-Xms1024M -J-Xmx2048M -J-server \
  scalafmtCheckAll \
  scalafmtSbtCheck \
  Test/compile \
  doc \
  crossTestBridges \
  test \
  scripted
