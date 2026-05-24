package irai.mod.reforge.Config;

/**
 * Allows configs to merge in newly added built-in defaults without
 * overwriting values already present in the saved config.
 */
public interface ConfigDefaultInjector {
    /**
     * @return true when the config mutated and should be saved back to disk.
     */
    boolean injectMissingDefaults();
}
