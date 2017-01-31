package xsbti;


import java.util.EnumSet;

public enum UseScope {
    // Order of declaration is crucial
    // We can support only 6 values with current implementation of mapper
    Default, Implicit, PatMatTarget
}

