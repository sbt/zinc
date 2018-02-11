/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import xsbti.Logger;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Define a helper class to instantiate {@link IncOptions}.
 */
public class IncOptionsUtil {
  /*
   * 1. recompile changed sources
   * 2. recompile direct dependencies and transitive public inheritance dependencies of sources with API changes in 1.
   * 3. further changes invalidate all dependencies transitively to avoid too many steps.
   */

    public static final String TRANSITIVE_STEP_KEY = "transitiveStep";
    public static final String RECOMPILE_ALL_FRACTION_KEY = "recompileAllFraction";
    public static final String RELATIONS_DEBUG_KEY = "relationsDebug";
    public static final String API_DEBUG_KEY = "apiDebug";
    public static final String API_DIFF_CONTEXT_SIZE_KEY = "apiDiffContextSize";
    public static final String API_DUMP_DIRECTORY_KEY = "apiDumpDirectory";
    public static final String RECOMPILE_ON_MACRO_DEF_KEY = "recompileOnMacroDef";
    public static final String USE_OPTIMIZED_SEALED = "useOptimizedSealed";
    public static final String LOG_RECOMPILE_ON_MACRO = "logRecompileOnMacro";
    public static final String CLASSFILE_MANAGER_TYPE_KEY = "classfileManagerType";
    public static final String TRANSACTIONAL_MANAGER_TYPE = "transactionalManagerType";
    public static final String TRANSACTIONAL_MANAGER_BASE_DIRECTORY = "transactionalManagerBaseDirectory";
    public static final String DELETE_IMMEDIATELY_MANAGER_TYPE = "deleteImmediatelyManagerType";
    private static final String XSBTI_NOTHING = "NOTHING";

    // Small utility function for logging
    private static Supplier<String> f0(String message) {
        return new Supplier<String>() {
            @Override
            public String get() {
                return message;
            }
        };
    }

    /**
     * Reads and returns an instance of {@link IncOptions} from a mapping of values.
     *
     * @param values The values read from a properties file.
     * @param logger The logger used for reporting **and** for the transactional manager type.
     * @return An instance of {@link IncOptions}.
     */
    public static IncOptions fromStringMap(Map<String, String> values, Logger logger) {
        IncOptions base = IncOptions.of();
        logger.debug(f0("Reading incremental options from map"));

        if (values.containsKey(TRANSITIVE_STEP_KEY)) {
            logger.debug(f0("TRANSITIVE_STEP_KEY value was read."));
            base = base.withTransitiveStep(Integer.parseInt(values.get(TRANSITIVE_STEP_KEY)));
        }

        if (values.containsKey(RECOMPILE_ALL_FRACTION_KEY)) {
            logger.debug(f0("RECOMPILE_ALL_FRACTION_KEY value was read."));
            base = base.withRecompileAllFraction(Double.parseDouble(values.get(RECOMPILE_ALL_FRACTION_KEY)));
        }

        if (values.containsKey(RELATIONS_DEBUG_KEY)) {
            logger.debug(f0("RELATIONS_DEBUG_KEY value was read."));
            base = base.withRelationsDebug(Boolean.parseBoolean(values.get(RELATIONS_DEBUG_KEY)));
        }

        if (values.containsKey(API_DEBUG_KEY)) {
            logger.debug(f0("API_DEBUG_KEY value was read."));
            base = base.withApiDebug(Boolean.parseBoolean(values.get(API_DEBUG_KEY)));
        }

        if (values.containsKey(API_DIFF_CONTEXT_SIZE_KEY)) {
            logger.debug(f0("API_DIFF_CONTENT_SIZE_KEY value was read."));
            base = base.withApiDiffContextSize(Integer.parseInt(values.get(API_DIFF_CONTEXT_SIZE_KEY)));
        }

        if (values.containsKey(API_DUMP_DIRECTORY_KEY)) {
            if (values.get(API_DUMP_DIRECTORY_KEY).equals(XSBTI_NOTHING)) {
                base = base.withApiDumpDirectory(Optional.empty());
            } else {
                logger.debug(f0("API_DUMP_DIRECTORY_KEY value was read."));
                base = base.withApiDumpDirectory(Optional.of(new File(values.get(API_DUMP_DIRECTORY_KEY))));
            }
        }

        if (values.containsKey(CLASSFILE_MANAGER_TYPE_KEY)) {
            String value = values.get(CLASSFILE_MANAGER_TYPE_KEY);
            if (value.equals(XSBTI_NOTHING)) {
                base.withClassfileManagerType(Optional.empty());
            } else {
                logger.debug(f0("CLASS_FILE_MANAGER_TYPE_KEY value was read."));
                if (value.equals(TRANSACTIONAL_MANAGER_TYPE)) {
                    if (values.containsKey(TRANSACTIONAL_MANAGER_BASE_DIRECTORY)) {
                        File baseDirectory = new File(values.get(TRANSACTIONAL_MANAGER_BASE_DIRECTORY));
                        base.withClassfileManagerType(Optional.of(new TransactionalManagerType(baseDirectory, logger)));
                    } else {
                        logger.warn(f0("Missing " + TRANSACTIONAL_MANAGER_BASE_DIRECTORY + " key for specified transactional classfile manager."));
                        logger.warn(f0("Classfile manager defaults to delete immediately manager type."));
                        base.withClassfileManagerType(Optional.of(new DeleteImmediatelyManagerType()));
                    }
                } else if (value.equals(DELETE_IMMEDIATELY_MANAGER_TYPE)) {
                    base.withClassfileManagerType(Optional.of(new DeleteImmediatelyManagerType()));
                } else {
                    logger.warn(f0("Unrecognised classfile manager type key " + value + "."));
                    logger.warn(f0("Classfile manager defaults to delete immediately manager type."));
                    // Default case -- if value is not understood, pick DeleteImmediatelyManagerType
                    base.withClassfileManagerType(Optional.of(new DeleteImmediatelyManagerType()));
                }
            }
        }

        if (values.containsKey(RECOMPILE_ON_MACRO_DEF_KEY)) {
            if (values.get(RECOMPILE_ON_MACRO_DEF_KEY).equals(XSBTI_NOTHING)) {
                base = base.withRecompileOnMacroDef(Optional.empty());
            } else {
                logger.debug(f0("RECOMPILE_ON_MACRO_DEF_KEY value was read."));
                base = base.withRecompileOnMacroDef(Optional.of(Boolean.parseBoolean(values.get(RECOMPILE_ON_MACRO_DEF_KEY))));
            }
        }

        if (values.containsKey(LOG_RECOMPILE_ON_MACRO)) {
            logger.debug(f0("LOG_RECOMPILE_ON_MACRO value was read."));
            base = base.withLogRecompileOnMacro(Boolean.parseBoolean(values.get(LOG_RECOMPILE_ON_MACRO)));
        }

        if (values.containsKey(USE_OPTIMIZED_SEALED)) {
            logger.debug(f0("USE_OPTIMIZED_SEALED value was read."));
            base = base.withUseOptimizedSealed(Boolean.parseBoolean(values.get(USE_OPTIMIZED_SEALED)));
        }

        return base;
    }
}
