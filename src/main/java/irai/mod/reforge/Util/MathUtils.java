package irai.mod.reforge.Util;

/**
 * Common mathematical utilities used throughout SocketReforge.
 */
public final class MathUtils {
    private MathUtils() {} // Prevent instantiation

    /**
     * Clamps a value to the range [0.0, 1.0].
     * Useful for probability and percentage values.
     *
     * @param value the value to clamp
     * @return value clamped between 0.0 and 1.0 inclusive
     */
    public static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    /**
     * Clamps a value to the range [min, max].
     *
     * @param value the value to clamp
     * @param min   the minimum bound
     * @param max   the maximum bound
     * @return value clamped between min and max inclusive
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Clamps an integer value to the range [min, max].
     *
     * @param value the value to clamp
     * @param min   the minimum bound
     * @param max   the maximum bound
     * @return value clamped between min and max inclusive
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
