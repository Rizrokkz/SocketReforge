package irai.mod.reforge.Commands;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;

import irai.mod.reforge.Common.ArmorAffinityResistanceUtils;
import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.WeaponElementalDamageUtils;
import irai.mod.reforge.Entity.Events.SocketStatSystem;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.UI.EquipmentStatsUI;

/**
 * Shows the player's currently equipped armor stats and held weapon output.
 */
public class EquipmentStatsCommand extends CommandBase {
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    public EquipmentStatsCommand(@NonNullDecl String name,
                                 @NonNullDecl String description,
                                 boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
        this.addAliases("equipstats");
        this.addAliases("playerstats");
        this.requirePermission(HytalePermissions.fromCommand("op.add"));
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }
        if (EquipmentStatsUI.isAvailable()) {
            EquipmentStatsUI.open(player);
            return;
        }

        context.sendMessage(Message.raw("=== Socket Reforge Equipment Stats ==="));
        sendWeaponStats(context, player);
        sendArmorStats(context, player);
    }

    private static void sendWeaponStats(CommandContext context, Player player) {
        ItemStack held = PlayerInventoryUtils.getHeldItem(player);
        if (held == null || held.isEmpty() || !ReforgeEquip.isWeapon(held)) {
            context.sendMessage(Message.raw("Weapon: none held"));
            return;
        }

        SocketData socketData = SocketManager.getSocketData(held);
        int level = ReforgeEquip.getLevelFromItem(held);
        double partsMultiplier = partsDamageMultiplier(held);
        double softcoreMultiplier = ReforgeEquip.getSoftcoreStatMultiplier(held);
        EquipmentDamageTooltipMath.StatSummary summary = EquipmentDamageTooltipMath.computeWeaponDamageSummary(
                held.getItemId(),
                level,
                socketData,
                partsMultiplier,
                softcoreMultiplier);

        double[] damage = bonus(held, socketData, EssenceEffect.StatType.DAMAGE, true);
        double[] attackSpeed = bonus(held, socketData, EssenceEffect.StatType.ATTACK_SPEED, true);
        double[] critChance = bonus(held, socketData, EssenceEffect.StatType.CRIT_CHANCE, true);
        double[] critDamage = bonus(held, socketData, EssenceEffect.StatType.CRIT_DAMAGE, true);
        double[] lifeSteal = bonus(held, socketData, EssenceEffect.StatType.LIFE_STEAL, true);

        double normal = summary.getBuffedValue();
        double crit = normal * (1.0 + (critDamage[1] / 100.0));
        double expected = normal * (1.0 + ((critChance[1] / 100.0) * (critDamage[1] / 100.0)));

        StringBuilder sb = new StringBuilder();
        sb.append("Weapon: ").append(displayName(held)).append(" +").append(level).append('\n');
        sb.append("  Damage: ").append(format(summary.getBaseValue())).append(" -> ").append(format(normal));
        if (summary.getBaseValue() > 0.0) {
            sb.append(" (x").append(format(normal / summary.getBaseValue())).append(")");
        }
        sb.append('\n');
        sb.append("  Socket Damage Bonus: ").append(bonusLine(damage)).append('\n');
        sb.append("  Attack Speed: ").append(signedPercent(attackSpeed[1])).append('\n');
        sb.append("  Crit: ").append(formatPercent(critChance[1])).append(" chance / ")
                .append(formatPercent(critDamage[1])).append(" damage").append('\n');
        sb.append("  Crit Hit: ").append(format(crit)).append(" | Expected Avg: ").append(format(expected)).append('\n');
        if (hasBonus(lifeSteal)) {
            sb.append("  Life Steal: ").append(bonusLine(lifeSteal)).append('\n');
        }
        appendWeaponBreakdown(sb, held, level, socketData, partsMultiplier, softcoreMultiplier);
        appendWeaponElements(sb, held, normal);
        context.sendMessage(Message.raw(sb.toString()));
    }

    private static void sendArmorStats(CommandContext context, Player player) {
        List<ItemStack> armorPieces = PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor);
        if (armorPieces.isEmpty()) {
            context.sendMessage(Message.raw("Armor: none equipped"));
            return;
        }

        double totalBaseDefense = 0.0;
        double totalCurrentDefense = 0.0;
        double[] health = new double[] {0.0, 0.0};
        double[] regen = new double[] {0.0, 0.0};
        double[] evasion = new double[] {0.0, 0.0};
        double[] blockChance = new double[] {0.0, 0.0};
        double[] slow = new double[] {0.0, 0.0};

        StringBuilder pieces = new StringBuilder();
        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            int level = ReforgeEquip.getLevelFromItem(armor);
            EquipmentDamageTooltipMath.StatSummary summary = EquipmentDamageTooltipMath.computeArmorDefenseSummary(
                    armor.getItemId(),
                    level,
                    socketData,
                    ReforgeEquip.getSoftcoreStatMultiplier(armor));
            totalBaseDefense += summary.getBaseValue();
            totalCurrentDefense += summary.getBuffedValue();
            addInto(health, bonus(armor, socketData, EssenceEffect.StatType.HEALTH, false));
            addInto(regen, bonus(armor, socketData, EssenceEffect.StatType.REGENERATION, false));
            addInto(evasion, bonus(armor, socketData, EssenceEffect.StatType.EVASION, false));
            addInto(blockChance, bonus(armor, socketData, EssenceEffect.StatType.BLOCK_CHANCE, false));
            addInto(slow, bonus(armor, socketData, EssenceEffect.StatType.MOVEMENT_SPEED, false));
            pieces.append("  - ").append(displayName(armor)).append(" +").append(level)
                    .append(": ").append(format(summary.getBaseValue()))
                    .append(" -> ").append(format(summary.getBuffedValue()))
                    .append('\n');
        }

        SocketStatSystem.DefensiveBonuses defensive = SocketStatSystem.getDefensiveBonuses(player);
        StringBuilder sb = new StringBuilder();
        sb.append("Armor: ").append(armorPieces.size()).append(" equipped").append('\n');
        sb.append("  Defense Total: ").append(format(totalBaseDefense)).append(" -> ")
                .append(format(totalCurrentDefense)).append('\n');
        sb.append(pieces);
        appendArmorBonus(sb, "Health", health);
        appendArmorBonus(sb, "Regeneration", regen);
        appendArmorBonus(sb, "Evasion", evasion);
        appendArmorBonus(sb, "Block Chance", blockChance);
        appendArmorBonus(sb, "Movement Slow", slow);
        sb.append("  Active Defensive Cache: defense ")
                .append(formatPercent(defensive.defensePercent()))
                .append(", evasion ")
                .append(formatPercent(defensive.evasionPercent()))
                .append(", block ")
                .append(formatPercent(defensive.blockChancePercent()))
                .append('\n');
        appendArmorResistances(sb, armorPieces);
        context.sendMessage(Message.raw(sb.toString()));
    }

    private static void appendWeaponBreakdown(StringBuilder sb,
                                              ItemStack weapon,
                                              int level,
                                              SocketData socketData,
                                              double partsMultiplier,
                                              double softcoreMultiplier) {
        List<EquipmentDamageTooltipMath.DamageBreakdown> breakdowns =
                EquipmentDamageTooltipMath.getDamageBreakdownFromInteractionVars(weapon.getItemId());
        if (breakdowns.isEmpty()) {
            return;
        }
        sb.append("  Hit Breakdown:").append('\n');
        for (EquipmentDamageTooltipMath.DamageBreakdown breakdown : breakdowns) {
            double buffed = EquipmentDamageTooltipMath.computeBuffedWeaponDamage(
                    weapon.getItemId(),
                    breakdown.getBaseValue(),
                    level,
                    socketData,
                    partsMultiplier,
                    softcoreMultiplier);
            sb.append("    ").append(breakdown.getLabel()).append(": ")
                    .append(format(breakdown.getBaseValue()))
                    .append(" -> ")
                    .append(format(buffed))
                    .append('\n');
        }
    }

    private static void appendWeaponElements(StringBuilder sb, ItemStack weapon, double damageOutput) {
        List<WeaponElementalDamageUtils.ElementDamage> elements =
                WeaponElementalDamageUtils.calculateElementDamage(weapon, damageOutput);
        if (elements.isEmpty()) {
            return;
        }
        sb.append("  Elemental Damage:").append('\n');
        for (WeaponElementalDamageUtils.ElementDamage element : elements) {
            sb.append("    ").append(formatEssenceType(element.type())).append(": ")
                    .append(format(element.damage()))
                    .append(" (")
                    .append(formatPercent(element.rate() * 100.0))
                    .append(")")
                    .append('\n');
        }
    }

    private static void appendArmorResistances(StringBuilder sb, List<ItemStack> armorPieces) {
        Map<Essence.Type, Double> resistances =
                ArmorAffinityResistanceUtils.calculateResistancePercentByIncomingType(armorPieces);
        if (!ArmorAffinityResistanceUtils.hasAnyResistance(resistances)) {
            return;
        }
        sb.append("  Element Resistances:").append('\n');
        for (Essence.Type type : Essence.Type.values()) {
            double percent = resistances.getOrDefault(type, 0.0);
            if (percent <= 0.0001) {
                continue;
            }
            sb.append("    ").append(formatEssenceType(type)).append(": ")
                    .append(formatPercent(percent))
                    .append('\n');
        }
    }

    private static void appendArmorBonus(StringBuilder sb, String label, double[] bonus) {
        if (!hasBonus(bonus)) {
            return;
        }
        sb.append("  ").append(label).append(": ").append(bonusLine(bonus)).append('\n');
    }

    private static double[] bonus(ItemStack item, SocketData socketData, EssenceEffect.StatType stat, boolean isWeapon) {
        double[] stored = normalizeBonus(SocketManager.getStoredStatBonus(item, stat));
        if (hasBonus(stored) || socketData == null) {
            return stored;
        }
        return normalizeBonus(SocketManager.calculateTieredBonus(socketData, stat, isWeapon));
    }

    private static void addInto(double[] total, double[] bonus) {
        total[0] += bonus[0];
        total[1] += bonus[1];
    }

    private static double[] normalizeBonus(double[] bonus) {
        if (bonus == null || bonus.length < 2) {
            return new double[] {0.0, 0.0};
        }
        return new double[] {bonus[0], bonus[1]};
    }

    private static boolean hasBonus(double[] bonus) {
        return bonus != null
                && bonus.length >= 2
                && (Math.abs(bonus[0]) > 0.0001 || Math.abs(bonus[1]) > 0.0001);
    }

    private static double partsDamageMultiplier(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 1.0;
        }
        Double value = item.getFromMetadataOrNull("SocketReforge.Parts.DamageMultiplier", Codec.DOUBLE);
        if (value == null || !Double.isFinite(value)) {
            return 1.0;
        }
        return Math.max(0.5, Math.min(2.0, value));
    }

    private static String displayName(ItemStack item) {
        String name = CommandUtils.getItemDisplayName(item);
        return name == null || name.isBlank() ? item.getItemId() : name;
    }

    private static String bonusLine(double[] bonus) {
        boolean hasFlat = Math.abs(bonus[0]) > 0.0001;
        boolean hasPercent = Math.abs(bonus[1]) > 0.0001;
        if (!hasFlat && !hasPercent) {
            return "+0";
        }
        if (hasFlat && hasPercent) {
            return signedNumber(bonus[0]) + " / " + signedPercent(bonus[1]);
        }
        if (hasFlat) {
            return signedNumber(bonus[0]);
        }
        return signedPercent(bonus[1]);
    }

    private static String signedNumber(double value) {
        return (value >= 0.0 ? "+" : "") + format(value);
    }

    private static String signedPercent(double percent) {
        return (percent >= 0.0 ? "+" : "") + formatPercent(percent);
    }

    private static String formatPercent(double percent) {
        return format(percent) + "%";
    }

    private static String format(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        synchronized (NUMBER_FORMAT) {
            return NUMBER_FORMAT.format(value);
        }
    }

    private static String formatEssenceType(Essence.Type type) {
        if (type == null) {
            return "None";
        }
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}
