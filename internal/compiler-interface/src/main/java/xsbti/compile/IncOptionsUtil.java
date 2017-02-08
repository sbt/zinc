/*
 * Zinc - The incremental compiler for Scala.
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * This software is released under the terms written in LICENSE.
 */

package xsbti.compile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import xsbti.Maybe;

/**
 * Helper object for xsbti.compile.IncOptions
 */
public class IncOptionsUtil {
  /**
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
  public static final String CLASSFILE_MANAGER_TYPE_KEY = "classfileManagerType";
  public static final String RECOMPILE_ON_MACRO_DEF_KEY = "recompileOnMacroDef";
  public static final String NAME_HASHING_KEY = "nameHashing";
  public static final String ANT_STYLE_KEY = "antStyle";
  public static final String LOG_RECOMPILE_ON_MACRO = "logRecompileOnMacro";
  private static final String XSBTI_NOTHING = "NOTHING";

  public static int defaultTransitiveStep() {
    return 3;
  }

  public static double defaultRecompileAllFraction() {
    return 0.5;
  }

  public static boolean defaultRelationsDebug() {
    return false;
  }

  public static boolean defaultApiDebug() {
    return false;
  }

  public static int defaultApiDiffContextSize() {
    return 5;
  }

  public static Maybe<File> defaultApiDumpDirectory() {
    return Maybe.<File>nothing();
  }

  public static Maybe<ClassfileManagerType> defaultClassfileManagerType() {
    return Maybe.<ClassfileManagerType>nothing();
  }

  public static Maybe<Boolean> defaultRecompileOnMacroDef() {
    return Maybe.<Boolean>nothing();
  }

  public static boolean defaultRecompileOnMacroDefImpl() {
    return true;
  }

  public static boolean getRecompileOnMacroDef(IncOptions options) {
    if (options.recompileOnMacroDef().isDefined()) {
      return options.recompileOnMacroDef().get();
    } else {
      return defaultRecompileOnMacroDefImpl();
    }
  }

  public static boolean defaultNameHashing() {
    return true;
  }

  public static boolean defaultUseCustomizedFileManager() {
    return false;
  }

  public static boolean defaultStoreApis() {
    return true;
  }

  public static boolean defaultEnabled() {
    return true;
  }

  public static boolean defaultAntStyle() {
    return false;
  }

  public static Map<String, String> defaultExtra() {
    return new HashMap<String, String>();
  }

  public static ExternalHooks defaultExternal(){
    return new ExternalHooks() {
      @Override
      public Lookup externalLookup() {
        return null;
      }

      @Override
      public ClassFileManager externalClassFileManager() {
        return null;
      }
    };
  }

  public static boolean defaultLogRecompileOnMacro() {
    return true;
  }

  public static IncOptions defaultIncOptions() {
    IncOptions retval = new IncOptions(
      defaultTransitiveStep(), defaultRecompileAllFraction(),
      defaultRelationsDebug(), defaultApiDebug(),
      defaultApiDiffContextSize(), defaultApiDumpDirectory(),
      defaultClassfileManagerType(), defaultUseCustomizedFileManager(),
      defaultRecompileOnMacroDef(), defaultNameHashing(),
      defaultStoreApis(), defaultEnabled(), defaultAntStyle(),
      defaultExtra(), defaultLogRecompileOnMacro(),
      defaultExternal());
    return retval;
  }

  public static IncOptions fromStringMap(Map<String, String> values) {
    IncOptions base = defaultIncOptions();

    if (values.containsKey(TRANSITIVE_STEP_KEY)) {
      base = base.withTransitiveStep(Integer.parseInt(values.get(TRANSITIVE_STEP_KEY)));
    }

    if (values.containsKey(RECOMPILE_ALL_FRACTION_KEY)) {
      base = base.withRecompileAllFraction(Double.parseDouble(values.get(RECOMPILE_ALL_FRACTION_KEY)));
    }

    if (values.containsKey(RELATIONS_DEBUG_KEY)) {
      base = base.withRelationsDebug(Boolean.parseBoolean(values.get(RELATIONS_DEBUG_KEY)));
    }

    if (values.containsKey(API_DEBUG_KEY)) {
      base = base.withApiDebug(Boolean.parseBoolean(values.get(API_DEBUG_KEY)));
    }

    if (values.containsKey(API_DIFF_CONTEXT_SIZE_KEY)) {
      base = base.withApiDiffContextSize(Integer.parseInt(values.get(API_DIFF_CONTEXT_SIZE_KEY)));
    }

    if (values.containsKey(API_DUMP_DIRECTORY_KEY)) {
      if (values.get(API_DUMP_DIRECTORY_KEY).equals(XSBTI_NOTHING)) {
        base = base.withApiDumpDirectory(xsbti.Maybe.<File>nothing());
      } else {
        base = base.withApiDumpDirectory(xsbti.Maybe.<File>just(new File(values.get(API_DUMP_DIRECTORY_KEY))));
      }
    }

    // TODO: Figure out how to specify the class file manager type.
    // if (values.containsKey(CLASSFILE_MANAGER_TYPE_KEY)) {
    //   if (values.get(CLASSFILE_MANAGER_TYPE_KEY).equals(XSBTI_NOTHING)) {
    //     base = base.withClassfileManagerType(xsbti.Maybe.nothing<ClassfileManagerType>());
    //   } else {
    //     base = base.withClassfileManagerType(???)
    //   }
    // }

    if (values.containsKey(RECOMPILE_ON_MACRO_DEF_KEY)) {
      if (values.get(RECOMPILE_ON_MACRO_DEF_KEY).equals(XSBTI_NOTHING)) {
        base = base.withRecompileOnMacroDef(xsbti.Maybe.<Boolean>nothing());
      } else {
        base = base.withRecompileOnMacroDef(xsbti.Maybe.<Boolean>just(Boolean.parseBoolean(values.get(RECOMPILE_ON_MACRO_DEF_KEY))));
      }
    }

    if (values.containsKey(NAME_HASHING_KEY)) {
      base = base.withNameHashing(Boolean.parseBoolean(values.get(NAME_HASHING_KEY)));
    }

    if (values.containsKey(ANT_STYLE_KEY)) {
      base = base.withAntStyle(Boolean.parseBoolean(values.get(ANT_STYLE_KEY)));
    }

    if (values.containsKey(LOG_RECOMPILE_ON_MACRO)) {
      base = base.withLogRecompileOnMacro(Boolean.parseBoolean(values.get(LOG_RECOMPILE_ON_MACRO)));
    }

    return base;
  }
}
