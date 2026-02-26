package irai.mod.reforge.Util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A reusable logging utility that supports conditional logging and custom loggers.
 * This class provides a flexible logging solution that can be enabled/disabled
 * and can use custom logger implementations.
 */
public class ReforgeLogger {
    
    /**
     * Custom logger interface for flexible logging implementations.
     */
    public interface Logger {
        void info(String message);
        void warn(String message);
        void error(String message);
        void debug(String message);
    }
    
    /**
     * Default logger that outputs to System.out/System.err
     */
    public static class DefaultLogger implements Logger {
        private final String prefix;
        
        public DefaultLogger(String prefix) {
            this.prefix = prefix;
        }
        
        @Override
        public void info(String message) {
            System.out.println(prefix + " " + message);
        }
        
        @Override
        public void warn(String message) {
            System.out.println(prefix + " [WARNING] " + message);
        }
        
        @Override
        public void error(String message) {
            System.err.println(prefix + " [ERROR] " + message);
        }
        
        @Override
        public void debug(String message) {
            // Default logger doesn't output debug by default
        }
    }
    
    // Global enable/disable
    private static boolean enabled = false;
    private static Logger defaultLogger = new DefaultLogger("[Reforge]");
    private static Logger logger = defaultLogger;
    
    // Named loggers cache
    private static final Map<String, Logger> loggers = new HashMap<>();
    
    /**
     * Enable or disable all logging globally.
     */
    public static void setEnabled(boolean enabled) {
        ReforgeLogger.enabled = enabled;
    }
    
    /**
     * Check if logging is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Set the default logger used for all named loggers.
     */
    public static void setLogger(Logger logger) {
        ReforgeLogger.logger = logger != null ? logger : defaultLogger;
    }
    
    /**
     * Get a named logger instance.
     */
    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, n -> new DefaultLogger("[" + n + "]"));
    }
    
    /**
     * Log an info message if logging is enabled.
     */
    public static void info(String message) {
        if (enabled) {
            logger.info(message);
        }
    }
    
    /**
     * Log a warning message if logging is enabled.
     */
    public static void warn(String message) {
        if (enabled) {
            logger.warn(message);
        }
    }
    
    /**
     * Log an error message if logging is enabled.
     */
    public static void error(String message) {
        if (enabled) {
            logger.error(message);
        }
    }
    
    /**
     * Log a debug message if logging is enabled.
     */
    public static void debug(String message) {
        if (enabled) {
            logger.debug(message);
        }
    }
    
    /**
     * Log an info message using a named logger.
     */
    public static void info(String name, String message) {
        if (enabled) {
            getLogger(name).info(message);
        }
    }
    
    /**
     * Log a warning message using a named logger.
     */
    public static void warn(String name, String message) {
        if (enabled) {
            getLogger(name).warn(message);
        }
    }
    
    /**
     * Log an error message using a named logger.
     */
    public static void error(String name, String message) {
        if (enabled) {
            getLogger(name).error(message);
        }
    }
    
    /**
     * Log a debug message using a named logger.
     */
    public static void debug(String name, String message) {
        if (enabled) {
            getLogger(name).debug(message);
        }
    }
    
    /**
     * Log an info message with lazy evaluation (only evaluated if logging is enabled).
     */
    public static void info(Supplier<String> messageSupplier) {
        if (enabled) {
            logger.info(messageSupplier.get());
        }
    }
    
    /**
     * Log a debug message with lazy evaluation (only evaluated if logging is enabled).
     */
    public static void debug(Supplier<String> messageSupplier) {
        if (enabled) {
            logger.debug(messageSupplier.get());
        }
    }
    
    /**
     * Clear all cached loggers.
     */
    public static void clearLoggers() {
        loggers.clear();
    }
}
