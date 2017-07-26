#!/usr/bin/env bash
set -eu
set -o nounset
SCALA_VERSION="$1"

sbt -Dfile.encoding=UTF-8 \
  -J-XX:ReservedCodeCacheSize=256M \
  -J-Xmx3046M -J-Xms3046M -J-server \
  zincRoot/test:compile \
# drop scalafmt on the 1.0.0 branch to dogfood 1.0.0-RC2 before there is a sbt 1.0 of new-sbt-scalafnt
#  see https://github.com/lucidsoftware/neo-sbt-scalafmt/pull/34
# scalafmt::test \
# test:scalafmt::test \
  crossTestBridges \
  "publishBridgesAndSet $SCALA_VERSION" \
  zincRoot/test \
  zincRoot/scripted
