package irai.mod.reforge.Lore;

import java.util.Locale;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.SoundCategory;

import irai.mod.reforge.Common.PlayerInventoryUtils;

/**
 * Shared animation/sound helpers for lore combat effects.
 */
public final class LoreAnimationUtils {
    private LoreAnimationUtils() {}

    public static void playSwingAnimation(Store<EntityStore> store,
                                          Ref<EntityStore> attackerRef,
                                          int swingIndex) {
        if (store == null || attackerRef == null) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        String animSet = resolvePlayerAnimationSet(player);
        String anim = resolveSwingAnimationId(animSet, swingIndex);
        if (anim == null) {
            ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
            String fallbackSet = resolveFallbackAnimationSet(stack);
            if (fallbackSet != null && !fallbackSet.equalsIgnoreCase(animSet)) {
                String fallbackAnim = resolveSwingAnimationId(fallbackSet, swingIndex);
                if (fallbackAnim != null) {
                    animSet = fallbackSet;
                    anim = fallbackAnim;
                }
            }
        }
        if (anim != null) {
            try {
                AnimationUtils.playAnimation(attackerRef, AnimationSlot.Action, animSet, anim, true, store);
            } catch (Throwable ignored) {
                // best-effort
            }
        }
        playSwingSound(store, player);
    }

    public static void playChargeAttackAnimation(Store<EntityStore> store,
                                                 Ref<EntityStore> attackerRef,
                                                 int swingIndex) {
        if (store == null || attackerRef == null) {
            return;
        }
        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        String anim = resolveChargeAnimationId(stack, swingIndex);
        if (anim == null || anim.isBlank()) {
            playSwingAnimation(store, attackerRef, swingIndex);
            return;
        }
        try {
            String animSet = resolvePlayerAnimationSet(player);
            AnimationUtils.playAnimation(attackerRef, AnimationSlot.Action, animSet, anim, true, store);
        } catch (Throwable ignored) {
            playSwingAnimation(store, attackerRef, swingIndex);
            return;
        }
        playChargeSound(store, player);
    }

    public static String resolvePlayerAnimationSet(Player player) {
        if (player == null) {
            return "Item";
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        if (stack == null || stack.isEmpty()) {
            return "Item";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        if (animId == null || animId.isBlank()) {
            return "Item";
        }
        return animId;
    }

    public static String resolveAnimationIdByContains(String animSet, String token) {
        if (animSet == null || animSet.isBlank() || token == null || token.isBlank()) {
            return null;
        }
        String tokenLower = token.toLowerCase(Locale.ROOT);
        try {
            var assetMap =
                    com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap();
            if (assetMap != null) {
                var anims = assetMap.getAsset(animSet);
                if (anims != null) {
                    var map = anims.getAnimations();
                    if (map != null) {
                        for (String key : map.keySet()) {
                            if (key == null) {
                                continue;
                            }
                            if (key.toLowerCase(Locale.ROOT).contains(tokenLower)) {
                                return key;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static String resolveSwingAnimationId(String animSet, int swingIndex) {
        if (animSet == null || animSet.isBlank()) {
            return null;
        }
        boolean left = swingIndex % 2 == 0;
        String anim = resolveAnimationId(
                animSet,
                left ? "SwingLeft" : "SwingRight",
                left ? "SwingLeftCharged" : "SwingRightCharged",
                left ? "SwingLeftAttack" : "SwingRightAttack",
                left ? "AttackLeft" : "AttackRight",
                "Attack",
                "Swing",
                "Slash",
                "Strike",
                "Stab"
        );
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "swing");
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "attack");
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "slash");
        if (anim != null) {
            return anim;
        }
        anim = resolveAnimationIdByContains(animSet, "strike");
        if (anim != null) {
            return anim;
        }
        return resolveAnimationIdByContains(animSet, "stab");
    }

    private static String resolveFallbackAnimationSet(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "Item";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        String key = animId != null && !animId.isBlank() ? animId : stack.getItemId();
        if (key == null || key.isBlank()) {
            return "Item";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("battleaxe")) {
            return "Battleaxe";
        }
        if (lower.contains("mace") || lower.contains("hammer") || lower.contains("club")) {
            return "Mace";
        }
        if (lower.contains("dagger") || lower.contains("daggers") || lower.contains("knife") || lower.contains("claw")) {
            return "Daggers";
        }
        if (lower.contains("sword") || lower.contains("sabre") || lower.contains("rapier")) {
            return "Sword";
        }
        if (lower.contains("axe")) {
            return "Axe";
        }
        return "Item";
    }

    private static String resolveAnimationId(String animSet, String... animIds) {
        if (animSet == null || animSet.isBlank() || animIds == null || animIds.length == 0) {
            return null;
        }
        try {
            var assetMap = com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.getAssetMap();
            if (assetMap != null) {
                var anims = assetMap.getAsset(animSet);
                if (anims != null) {
                    var map = anims.getAnimations();
                    if (map != null) {
                        for (String animId : animIds) {
                            if (animId == null || animId.isBlank()) {
                                continue;
                            }
                            if (map.containsKey(animId)) {
                                return animId;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // best-effort
        }
        return null;
    }

    private static void playSwingSound(Store<EntityStore> store, Player player) {
        if (store == null || player == null) {
            return;
        }
        ItemStack stack = PlayerInventoryUtils.getHeldItem(player);
        String soundId = resolveSwingSoundId(stack);
        if (soundId == null || soundId.isBlank()) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
        if (soundIndex < 0) {
            LoreDebug.logKv("sound.missing", "id", soundId);
            return;
        }
        try {
            SoundUtil.playSoundEvent2dToPlayer(player.getPlayerRef(), soundIndex, SoundCategory.SFX);
        } catch (Throwable ignored) {
            // best-effort
        }
    }

    private static void playChargeSound(Store<EntityStore> store, Player player) {
        if (store == null || player == null) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex("SFX_Weapon_Charge_Swing");
        if (soundIndex >= 0) {
            try {
                SoundUtil.playSoundEvent2dToPlayer(player.getPlayerRef(), soundIndex, SoundCategory.SFX);
                return;
            } catch (Throwable ignored) {
                // fallback below
            }
        }
        playSwingSound(store, player);
    }

    private static String resolveSwingSoundId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "SFX_Unarmed_Swing";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        String key = animId != null && !animId.isBlank() ? animId : stack.getItemId();
        if (key == null || key.isBlank()) {
            return "SFX_Unarmed_Swing";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("longsword")) {
            return "SFX_Longsword_Special_Swing";
        }
        if (lower.contains("battleaxe")) {
            return "SFX_Battleaxe_T2_Swing";
        }
        if (lower.contains("axe")) {
            return "SFX_Axe_Iron_Swing";
        }
        if (lower.contains("mace") || lower.contains("hammer") || lower.contains("club")) {
            return "SFX_Mace_T1_Swing";
        }
        if (lower.contains("spear") || lower.contains("staff") || lower.contains("polearm")) {
            return "SFX_Light_Melee_T1_Swing";
        }
        if (lower.contains("dagger") || lower.contains("daggers") || lower.contains("knife") || lower.contains("claw")) {
            return "SFX_Sword_T1_Swing";
        }
        if (lower.contains("sword") || lower.contains("sabre") || lower.contains("rapier")) {
            return "SFX_Sword_T1_Swing";
        }
        return "SFX_Unarmed_Swing";
    }

    private static String resolveChargeAnimationId(ItemStack stack, int swingIndex) {
        boolean left = swingIndex % 2 == 0;
        if (stack == null || stack.isEmpty()) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        Item item = stack.getItem();
        String animId = item == null ? null : item.getPlayerAnimationsId();
        String key = animId != null && !animId.isBlank() ? animId : stack.getItemId();
        if (key == null) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("dagger")) {
            return left ? "StabDoubleCharged" : "LungeDoubleCharged";
        }
        if (lower.contains("spear")) {
            return "ThrowCharged";
        }
        if (lower.contains("staff")) {
            return "CastSummonCharged";
        }
        if (lower.contains("bow") || lower.contains("crossbow")) {
            return "ShootCharged";
        }
        if (lower.contains("mace") || lower.contains("hammer") || lower.contains("club")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        if (lower.contains("battleaxe")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        if (lower.contains("axe")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        if (lower.contains("sword") || lower.contains("sabre") || lower.contains("rapier")) {
            return left ? "SwingLeftCharged" : "SwingRightCharged";
        }
        return left ? "SwingLeftCharged" : "SwingRightCharged";
    }
}
