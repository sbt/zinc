package xsbti.compile;

import xsbti.Logger;

import java.io.File;

/**
 *
 */
public interface CompilerBridgeProvider {

    /**
     * Get the location of the Scala bridge sources.
     *
     * @param scalaInstance The Scala instance for which the bridge should be retrieved for.
     * @param logger The logger.
     * @return The jar (more typically) or directory where the sources are stored.
     */
    File getBridgeSources(ScalaInstance scalaInstance, Logger logger);
}
