package irai.mod.reforge.Common;

/** Converts attack-speed bonuses into the chance for one reduced-damage follow-up hit. */
public final class AttackSpeedMultiHitMath {
    public static final double BASE_ATTACK_SPEED = 5.0d;
    public static final double MULTIHIT_CHANCE_PER_SPEED_POINT = 20.0d;
    public static final double MAX_MULTIHIT_CHANCE_PERCENT = 75.0d;
    public static final double EXTRA_HIT_DAMAGE_FRACTION = 0.35d;

    private AttackSpeedMultiHitMath() {}

    /** Percentage bonuses scale the base speed value; 15% turns 5.0 into 5.75. */
    public static double effectiveAttackSpeed(double totalBonusPercent) {
        if (!Double.isFinite(totalBonusPercent)) return BASE_ATTACK_SPEED;
        return Math.max(0.0d, BASE_ATTACK_SPEED * (1.0d + totalBonusPercent / 100.0d));
    }

    /** Each point gained above the base value grants 20 percentage points of proc chance. */
    public static double multiHitChancePercent(double totalBonusPercent) {
        double gainedSpeed = effectiveAttackSpeed(totalBonusPercent) - BASE_ATTACK_SPEED;
        return Math.max(0.0d, Math.min(MAX_MULTIHIT_CHANCE_PERCENT,
                gainedSpeed * MULTIHIT_CHANCE_PER_SPEED_POINT));
    }
}
