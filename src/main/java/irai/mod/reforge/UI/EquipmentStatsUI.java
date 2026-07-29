package irai.mod.reforge.UI;

import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import irai.mod.reforge.Common.ArmorAffinityResistanceUtils;
import irai.mod.reforge.Common.EquipmentDamageTooltipMath;
import irai.mod.reforge.Common.PlayerInventoryUtils;
import irai.mod.reforge.Common.UI.HyUIReflectionUtils;
import irai.mod.reforge.Common.UI.UIItemUtils;
import irai.mod.reforge.Common.UI.UITemplateUtils;
import irai.mod.reforge.Common.WeaponElementalDamageUtils;
import irai.mod.reforge.Entity.Events.SocketStatSystem;
import irai.mod.reforge.Interactions.ReforgeEquip;
import irai.mod.reforge.Socket.Essence;
import irai.mod.reforge.Socket.EssenceEffect;
import irai.mod.reforge.Socket.SocketData;
import irai.mod.reforge.Socket.SocketManager;
import irai.mod.reforge.Util.LangLoader;

/**
 * Read-only equipment stat summary UI.
 */
public final class EquipmentStatsUI {
    private static final String HYUI_PAGE_BUILDER = "au.ellie.hyui.builders.PageBuilder";
    private static final String HYUI_PLUGIN = "au.ellie.hyui.HyUIPlugin";
    private static final String TEMPLATE_PATH = "Common/UI/Custom/Pages/EquipmentStats.html";
    private static final DecimalFormat NUMBER_FORMAT =
            new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private static final Map<PlayerRef, Object> openPages = new ConcurrentHashMap<>();
    private static boolean hyuiAvailable = false;

    private EquipmentStatsUI() {}

    public static void initialize() {
        hyuiAvailable = HyUIReflectionUtils.detectHyUi(HYUI_PAGE_BUILDER, HYUI_PLUGIN, "EquipmentStatsUI");
    }

    public static boolean isAvailable() {
        return hyuiAvailable;
    }

    public static void open(Player player) {
        if (player == null) {
            return;
        }
        if (!hyuiAvailable) {
            player.getPlayerRef().sendMessage(Message.raw("Equipment stats UI is unavailable (HyUI missing)."));
            return;
        }
        PlayerRef ref = player.getPlayerRef();
        closePageIfOpen(ref);
        player.getWorld().execute(() -> openWithSync(player));
    }

    private static void openWithSync(Player player) {
        try {
            Class<?> pageBuilderClass = Class.forName(HYUI_PAGE_BUILDER);
            Method pageForPlayer = pageBuilderClass.getMethod("pageForPlayer", PlayerRef.class);
            Method fromHtml = pageBuilderClass.getMethod("fromHtml", String.class);
            Method withLifetime = pageBuilderClass.getMethod("withLifetime", CustomPageLifetime.class);
            Method openMethod = pageBuilderClass.getMethod("open", Class.forName("com.hypixel.hytale.component.Store"));

            PlayerRef ref = player.getPlayerRef();
            Object pageBuilder = pageForPlayer.invoke(null, ref);
            pageBuilder = fromHtml.invoke(pageBuilder, buildHtml(player));
            pageBuilder = withLifetime.invoke(pageBuilder, CustomPageLifetime.CanDismiss);
            Object page = openMethod.invoke(pageBuilder, HyUIReflectionUtils.getStore(ref));
            openPages.put(ref, page);
        } catch (Exception e) {
            System.err.println("[SocketReforge] EquipmentStatsUI open error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String buildHtml(Player player) {
        WeaponView weaponView = buildWeaponView(player);
        ArmorView armorView = buildArmorView(player);
        String html = loadTemplate();
        html = html.replace("{{title}}", esc("Equipment Stats"));
        html = html.replace("{{subtitle}}", esc("Calculated from your held weapon, equipped armor, refinement, sockets, resonance, and mutations."));
        html = html.replace("{{weaponTitle}}", esc("Held Weapon"));
        html = html.replace("{{weaponIcon}}", weaponView.iconHtml);
        html = html.replace("{{weaponName}}", esc(weaponView.name));
        html = html.replace("{{weaponSummary}}", esc(weaponView.summary));
        html = html.replace("{{weaponCritLine}}", esc(weaponView.critLine));
        html = html.replace("{{weaponBreakdownTitle}}", esc("Damage Breakdown"));
        html = html.replace("{{weaponRows}}", weaponView.rows);
        html = html.replace("{{weaponElementTitle}}", esc("Elemental Damage"));
        html = html.replace("{{weaponElementRows}}", weaponView.elementRows);
        html = html.replace("{{armorTitle}}", esc("Equipped Armor"));
        html = html.replace("{{armorSummary}}", esc(armorView.summary));
        html = html.replace("{{armorBonusLine}}", esc(armorView.bonusLine));
        html = html.replace("{{defensiveCacheLine}}", esc(armorView.cacheLine));
        html = html.replace("{{armorPiecesTitle}}", esc("Armor Pieces"));
        html = html.replace("{{armorRows}}", armorView.rows);
        html = html.replace("{{resistanceTitle}}", esc("Element Resistances"));
        html = html.replace("{{resistanceRows}}", armorView.resistanceRows);
        html = html.replace("{{footerText}}", esc("Use /equipmentstats again after swapping gear to refresh this panel."));
        return LangLoader.replaceUiTokens(player, html);
    }

    private static WeaponView buildWeaponView(Player player) {
        ItemStack held = PlayerInventoryUtils.getHeldItem(player);
        if (held == null || held.isEmpty() || !ReforgeEquip.isWeapon(held)) {
            return new WeaponView(
                    emptyIcon(),
                    "No weapon held",
                    "Hold a weapon to inspect calculated output.",
                    "Crit: -",
                    emptyRow("No weapon selected."),
                    emptyRow("No elemental weapon damage."));
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
        String summaryLine = "Damage " + format(summary.getBaseValue()) + " -> " + format(normal)
                + " | Expected " + format(expected);
        String critLine = "Crit " + formatPercent(critChance[1]) + " / +" + formatPercent(critDamage[1])
                + " | Crit hit " + format(crit);

        StringBuilder rows = new StringBuilder();
        rows.append(statRow("Refine Level", "+" + level, "#FFD36A"));
        rows.append(statRow("Damage Bonus", bonusLine(damage), "#FFAA55"));
        rows.append(statRow("Attack Speed", signedPercent(attackSpeed[1]), "#FFFFAA"));
        rows.append(statRow("Parts Multiplier", "x" + format(partsMultiplier), "#B9C7D6"));
        rows.append(statRow("Softcore Multiplier", "x" + format(softcoreMultiplier), "#B9C7D6"));
        if (hasBonus(lifeSteal)) {
            rows.append(statRow("Life Steal", bonusLine(lifeSteal), "#77DD77"));
        }
        appendWeaponBreakdown(rows, held, level, socketData, partsMultiplier, softcoreMultiplier);

        return new WeaponView(
                itemIcon(held, 80),
                UIItemUtils.displayNameOrItemId(held, player),
                summaryLine,
                critLine,
                rows.toString(),
                buildWeaponElementRows(player, held, normal));
    }

    private static ArmorView buildArmorView(Player player) {
        List<ItemStack> armorPieces = PlayerInventoryUtils.getEquippedArmor(player, ReforgeEquip::isArmor);
        if (armorPieces.isEmpty()) {
            return new ArmorView(
                    "No armor equipped",
                    "Equip armor to inspect defense, socket bonuses, and elemental resistances.",
                    "Active defensive cache: -",
                    emptyRow("No armor selected."),
                    emptyRow("No elemental resistances."));
        }

        double totalBase = 0.0;
        double totalCurrent = 0.0;
        double[] defense = new double[] {0.0, 0.0};
        double[] health = new double[] {0.0, 0.0};
        double[] regen = new double[] {0.0, 0.0};
        double[] evasion = new double[] {0.0, 0.0};
        double[] blockChance = new double[] {0.0, 0.0};
        double[] slow = new double[] {0.0, 0.0};
        StringBuilder rows = new StringBuilder();

        for (ItemStack armor : armorPieces) {
            SocketData socketData = SocketManager.getSocketData(armor);
            int level = ReforgeEquip.getLevelFromItem(armor);
            EquipmentDamageTooltipMath.StatSummary summary = EquipmentDamageTooltipMath.computeArmorDefenseSummary(
                    armor.getItemId(),
                    level,
                    socketData,
                    ReforgeEquip.getSoftcoreStatMultiplier(armor));
            totalBase += summary.getBaseValue();
            totalCurrent += summary.getBuffedValue();
            addInto(defense, bonus(armor, socketData, EssenceEffect.StatType.DEFENSE, false));
            addInto(health, bonus(armor, socketData, EssenceEffect.StatType.HEALTH, false));
            addInto(regen, bonus(armor, socketData, EssenceEffect.StatType.REGENERATION, false));
            addInto(evasion, bonus(armor, socketData, EssenceEffect.StatType.EVASION, false));
            addInto(blockChance, bonus(armor, socketData, EssenceEffect.StatType.BLOCK_CHANCE, false));
            addInto(slow, bonus(armor, socketData, EssenceEffect.StatType.MOVEMENT_SPEED, false));
            rows.append(armorRow(player, armor, level, summary));
        }

        SocketStatSystem.DefensiveBonuses cached = SocketStatSystem.getDefensiveBonuses(player);
        StringBuilder bonusLine = new StringBuilder();
        appendInlineBonus(bonusLine, "Defense", defense);
        appendInlineBonus(bonusLine, "Health", health);
        appendInlineBonus(bonusLine, "Regen", regen);
        appendInlineBonus(bonusLine, "Evasion", evasion);
        appendInlineBonus(bonusLine, "Block", blockChance);
        appendInlineBonus(bonusLine, "Slow", slow);
        if (bonusLine.isEmpty()) {
            bonusLine.append("No active armor socket bonuses.");
        }

        String cacheLine = "Active cache: defense " + formatPercent(cached.defensePercent())
                + ", evasion " + formatPercent(cached.evasionPercent())
                + ", block " + formatPercent(cached.blockChancePercent());
        return new ArmorView(
                "Defense " + format(totalBase) + " -> " + format(totalCurrent)
                        + " | Pieces " + armorPieces.size(),
                bonusLine.toString(),
                cacheLine,
                rows.toString(),
                buildArmorResistanceRows(player, armorPieces));
    }

    private static void appendWeaponBreakdown(StringBuilder rows,
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
        rows.append("<p style=\"font-weight:bold; color:#FFFFFF;\">Per Hit</p>");
        for (EquipmentDamageTooltipMath.DamageBreakdown breakdown : breakdowns) {
            double buffed = EquipmentDamageTooltipMath.computeBuffedWeaponDamage(
                    weapon.getItemId(),
                    breakdown.getBaseValue(),
                    level,
                    socketData,
                    partsMultiplier,
                    softcoreMultiplier);
            rows.append(statRow(breakdown.getLabel(),
                    format(breakdown.getBaseValue()) + " -> " + format(buffed),
                    "#D8DEE9"));
        }
    }

    private static String buildWeaponElementRows(Player player, ItemStack weapon, double damageOutput) {
        List<WeaponElementalDamageUtils.ElementDamage> elements =
                WeaponElementalDamageUtils.calculateElementDamage(weapon, damageOutput);
        if (elements.isEmpty()) {
            return emptyRow("No elemental weapon damage.");
        }
        StringBuilder sb = new StringBuilder();
        for (WeaponElementalDamageUtils.ElementDamage element : elements) {
            sb.append(statRow(localizeEssenceType(player, element.type()),
                    format(element.damage()) + " (" + formatPercent(element.rate() * 100.0) + ")",
                    essenceColor(element.type())));
        }
        return sb.toString();
    }

    private static String buildArmorResistanceRows(Player player, List<ItemStack> armorPieces) {
        Map<Essence.Type, Double> resistances =
                ArmorAffinityResistanceUtils.calculateResistancePercentByIncomingType(armorPieces);
        if (!ArmorAffinityResistanceUtils.hasAnyResistance(resistances)) {
            return emptyRow("No elemental resistances.");
        }
        StringBuilder sb = new StringBuilder();
        for (Essence.Type type : Essence.Type.values()) {
            double percent = resistances.getOrDefault(type, 0.0);
            if (percent <= 0.0001) {
                continue;
            }
            sb.append(statRow(localizeEssenceType(player, type),
                    formatPercent(percent),
                    essenceColor(type)));
        }
        return sb.toString();
    }

    private static String armorRow(Player player,
                                   ItemStack armor,
                                   int level,
                                   EquipmentDamageTooltipMath.StatSummary summary) {
        return "<div style=\"anchor-width:510; anchor-height:74; layout-mode:Left; spacing:10; background-color:#1a1a2b; padding:8;\">"
                + itemIcon(armor, 58)
                + "<div style=\"anchor-width:420; layout-mode:Top;\">"
                + "<p style=\"font-weight:bold; font-size:15; color:#FFFFFF;\">"
                + esc(UIItemUtils.displayNameOrItemId(armor, player)) + " +" + level + "</p>"
                + "<p style=\"font-size:13; color:#B9C7D6;\">Defense "
                + format(summary.getBaseValue()) + " -> " + format(summary.getBuffedValue()) + "</p>"
                + "</div></div>";
    }

    private static String statRow(String label, String value, String color) {
        return "<div style=\"anchor-width:510; layout-mode:Left; spacing:10; background-color:#1a1a2b; padding:6;\">"
                + "<p style=\"anchor-width:180; font-size:14; color:" + esc(color) + "; white-space:nowrap;\">"
                + esc(label) + "</p>"
                + "<p style=\"anchor-width:300; font-size:14; color:#FFFFFF; white-space:nowrap;\">"
                + esc(value) + "</p></div>";
    }

    private static String emptyRow(String text) {
        return "<p style=\"font-size:14; color:#B9C7D6;\">" + esc(text) + "</p>";
    }

    private static String itemIcon(ItemStack item, int size) {
        if (item == null || item.isEmpty()) {
            return emptyIcon();
        }
        return "<span class=\"item-icon\" data-hyui-item-id=\"" + esc(item.getItemId())
                + "\" style=\"anchor-width:" + size + "; anchor-height:" + size + ";\"></span>";
    }

    private static String emptyIcon() {
        return "<img src=\"slot_bg.png\" style=\"anchor-width:80; anchor-height:80;\">";
    }

    private static double[] bonus(ItemStack item, SocketData socketData, EssenceEffect.StatType stat, boolean isWeapon) {
        double[] stored = normalizeBonus(SocketManager.getStoredStatBonus(item, stat));
        if (hasBonus(stored) || socketData == null) {
            return stored;
        }
        return normalizeBonus(SocketManager.calculateTieredBonus(socketData, stat, isWeapon));
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

    private static void addInto(double[] total, double[] bonus) {
        total[0] += bonus[0];
        total[1] += bonus[1];
    }

    private static void appendInlineBonus(StringBuilder sb, String label, double[] bonus) {
        if (!hasBonus(bonus)) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(" | ");
        }
        sb.append(label).append(' ').append(bonusLine(bonus));
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

    private static String localizeEssenceType(Player player, Essence.Type type) {
        if (type == null) {
            return "Unknown";
        }
        String key = switch (type) {
            case FIRE -> "essence.type.fire";
            case WATER -> "essence.type.water";
            case ICE -> "essence.type.ice";
            case LIGHTNING -> "essence.type.lightning";
            case LIFE -> "essence.type.life";
            case VOID -> "essence.type.void";
        };
        String translated = LangLoader.getUITranslation(player, key);
        if (translated == null || translated.isBlank() || translated.equals(key)) {
            String raw = type.name().toLowerCase(Locale.ROOT);
            return raw.isEmpty() ? type.name() : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        }
        return translated;
    }

    private static String essenceColor(Essence.Type type) {
        if (type == null) {
            return "#D8DEE9";
        }
        return switch (type) {
            case FIRE -> "#FFAA55";
            case WATER -> "#5599FF";
            case ICE -> "#55FFFF";
            case LIGHTNING -> "#FFFF55";
            case LIFE -> "#55FF55";
            case VOID -> "#B388FF";
        };
    }

    private static String loadTemplate() {
        return UITemplateUtils.loadTemplate(
                EquipmentStatsUI.class,
                TEMPLATE_PATH,
                "<div><p>Equipment Stats UI template missing.</p></div>",
                "EquipmentStatsUI");
    }

    private static void closePageIfOpen(PlayerRef ref) {
        HyUIReflectionUtils.closePageIfOpen(openPages, ref);
    }

    private static String esc(String text) {
        return UITemplateUtils.escapeHtml(text);
    }

    private record WeaponView(String iconHtml,
                              String name,
                              String summary,
                              String critLine,
                              String rows,
                              String elementRows) {}

    private record ArmorView(String summary,
                             String bonusLine,
                             String cacheLine,
                             String rows,
                             String resistanceRows) {}
}
