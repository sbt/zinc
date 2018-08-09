#!/bin/bash

# This is a hack to validate the compilation of 2.13 compiler bridge without using sbt,
# which was used for bootstrapping the initial compiler bridge.
# In the future when Scala compiler breaks source compatibility, this script might come in handy.

# $ export SCALA_X_HOME=/usr/local/Cellar/scala@2.13/2.13.0-M2

if [[ -z "$SCALA_X_HOME" ]]; then
  echo "SCALA_X_HOME is not set!" 1>&2
  echo "Run 'export SCALA_X_HOME=/usr/local/Cellar/scala@2.13/2.13.0-M2' or equivalent."
  exit 1
fi

mkdir -p target/compiler-bridge/

"$SCALA_X_HOME/bin/scalac" \
-nowarn \
-classpath $HOME/.ivy2/cache/org.scala-sbt/compiler-interface/jars/compiler-interface-1.0.3.jar:$HOME/.ivy2/cache/org.scala-sbt/util-interface/jars/util-interface-1.0.2.jar \
-d target/compiler-bridge/ \
internal/compiler-bridge/src/main/scala/xsbt/API.scala \
internal/compiler-bridge/src/main/scala/xsbt/DelegatingReporter.scala \
internal/compiler-bridge/src/main/scala/xsbt/InteractiveConsoleInterface.scala  \
internal/compiler-bridge/src/main/scala/xsbt/ScaladocInterface.scala \
internal/compiler-bridge/src/main/scala/xsbt/Analyzer.scala \
internal/compiler-bridge/src/main/scala/xsbt/Dependency.scala \
internal/compiler-bridge/src/main/scala/xsbt/InteractiveConsoleResponse.scala \
internal/compiler-bridge/src/main/scala/xsbt/CallbackGlobal.scala \
internal/compiler-bridge/src/main/scala/xsbt/ExtractAPI.scala \
internal/compiler-bridge/src/main/scala/xsbt/JavaUtils.scala \
internal/compiler-bridge/src/main/scala/xsbt/ClassName.scala \
internal/compiler-bridge/src/main/scala/xsbt/ExtractUsedNames.scala \
internal/compiler-bridge/src/main/scala/xsbt/LocalToNonLocalClass.scala \
internal/compiler-bridge/src/main/scala/xsbt/Command.scala \
internal/compiler-bridge/src/main/scala/xsbt/GlobalHelpers.scala \
internal/compiler-bridge/src/main/scala/xsbt/LocateClassFile.scala \
internal/compiler-bridge/src/main/scala/xsbt/CompilerInterface.scala \
internal/compiler-bridge/src/main/scala/xsbt/InteractiveConsoleFactory.scala \
internal/compiler-bridge/src/main/scala/xsbt/Log.scala \
internal/compiler-bridge/src/main/scala/xsbt/InteractiveConsoleHelper.scala \
internal/compiler-bridge/src/main/scala/xsbt/Message.scala \
internal/compiler-bridge/src/main/scala_2.13/xsbt/Compat.scala \
internal/compiler-bridge/src/main/scala_2.13/xsbt/ConsoleInterface.scala
