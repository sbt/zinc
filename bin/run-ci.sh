#!/usr/bin/env bash
set -eu
set -o nounset
SCALA_VERSION="$1"

sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=256M \
  -J-Xmx3046M -J-Xms3046M -J-server \
  zincRoot/scalafmt::test zincRoot/test:scalafmt::test \
  "publishBridgesAndSet $SCALA_VERSION" \
  crossTestBridges \
  zincRoot/test \
  zincRoot/scripted
