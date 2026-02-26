package irai.mod.reforge.Commands;

import javax.annotation.Nonnull;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import irai.mod.reforge.Util.LangLoader;
import irai.mod.reforge.Util.NameResolver;

/**
 * Command to inspect item metadata (BSON-based).
 * Usage: /showmeta
 * Displays detailed metadata information about the held item to console.
 */
public class ItemMetaCommand extends CommandBase {

    public ItemMetaCommand(@NonNullDecl String name, @NonNullDecl String description, boolean requiresConfirmation) {
        super(name, description, requiresConfirmation);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        Player player = CommandUtils.getPlayer(context, true);
        if (player == null) {
            return;
        }

        ItemStack item = player.getInventory().getItemInHand();

        if (item == null || item.isEmpty()) {
            System.out.println("[ItemMeta] Item is null or empty");
            return;
        }

        System.out.println("[ItemMeta] === Item ID: " + item.getItemId() + " ===");

        // Use NameResolver to get the display name
        String displayName = NameResolver.getDisplayName(item);
        System.out.println("[ItemMeta] Display Name: " + displayName);

        // Get translation key
        String translationKey = NameResolver.getTranslationKey(item);
        System.out.println("[ItemMeta] Translation Key: " + translationKey);
        
        // Try to resolve the translation key directly from LangLoader
        if (translationKey != null) {
            String resolvedFromLang = LangLoader.resolveTranslation(translationKey);
            System.out.println("[ItemMeta] Resolved from LangLoader: " + resolvedFromLang);
            
            // Debug: show what keys we're trying
            System.out.println("[ItemMeta] LangLoader has " + LangLoader.getTranslationCount("en-US") + " entries for en-US");
        }

        // Print metadata
        BsonDocument meta = item.getMetadata();
        if (meta != null) {
            System.out.println("[ItemMeta] Metadata keys = " + meta.keySet());
            for (String key : meta.keySet()) {
                BsonValue val = meta.get(key);
                String valueStr;
                switch (val.getBsonType()) {
                    case INT32:
                        valueStr = String.valueOf(val.asInt32().getValue());
                        break;
                    case INT64:
                        valueStr = String.valueOf(val.asInt64().getValue());
                        break;
                    case STRING:
                        valueStr = val.asString().getValue();
                        break;
                    case DOUBLE:
                        valueStr = String.valueOf(val.asDouble().getValue());
                        break;
                    case BOOLEAN:
                        valueStr = String.valueOf(val.asBoolean().getValue());
                        break;
                    case ARRAY:
                        valueStr = val.asArray().getValues().toString();
                        break;
                    case DOCUMENT:
                        valueStr = val.asDocument().toJson();
                        break;
                    default:
                        valueStr = val.toString();
                }
                System.out.println("[ItemMeta]   meta[" + key + "] = " + valueStr);
            }
        } else {
            System.out.println("[ItemMeta] Metadata = null");
        }

        // Use reflection to inspect the item object
        try {
            Object itemObj = item.getItem();
            if (itemObj == null) {
                System.out.println("[ItemMeta] item.getItem() = null");
                return;
            }
            System.out.println("[ItemMeta] Item class = " + itemObj.getClass().getName());
            
            System.out.println("[ItemMeta] --- Fields ---");
            for (java.lang.reflect.Field f : itemObj.getClass().getFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(itemObj);
                    System.out.println("[ItemMeta]   field " + f.getName() + " (" + f.getType().getSimpleName() + ") = " + (val != null ? val : "null"));
                } catch (Exception e) {
                    System.out.println("[ItemMeta]   field " + f.getName() + " = <error: " + e.getMessage() + ">");
                }
            }
            
            System.out.println("[ItemMeta] --- Methods (0-arg, non-void) ---");
            for (java.lang.reflect.Method m : itemObj.getClass().getMethods()) {
                if (m.getParameterCount() != 0 || m.getReturnType().equals(void.class)) continue;
                if (m.getDeclaringClass().equals(Object.class)) continue;
                // Look for methods that might return localized name
                String methodName = m.getName().toLowerCase();
                if (methodName.contains("name") || methodName.contains("display") || methodName.contains("local") || methodName.contains("title")) {
                    try {
                        Object result = m.invoke(itemObj);
                        System.out.println("[ItemMeta]   " + m.getName() + "() -> " + m.getReturnType().getSimpleName() + " = " + (result != null ? result : "null"));
                    } catch (Exception ignored) {}
                }
            }
            
            // Also check TranslationProperties methods
            try {
                Object transProps = itemObj.getClass().getMethod("getTranslationProperties").invoke(itemObj);
                if (transProps != null) {
                    System.out.println("[ItemMeta] TranslationProperties class = " + transProps.getClass().getName());
                    for (java.lang.reflect.Method m : transProps.getClass().getMethods()) {
                        if (m.getParameterCount() != 0 || m.getReturnType().equals(void.class)) continue;
                        if (m.getDeclaringClass().equals(Object.class)) continue;
                        try {
                            Object result = m.invoke(transProps);
                            System.out.println("[ItemMeta]   TranslationProperties." + m.getName() + "() = " + (result != null ? result : "null"));
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            
            // Try to access the game's translation system via Universe
            try {
                com.hypixel.hytale.server.core.universe.Universe universe = com.hypixel.hytale.server.core.universe.Universe.get();
                System.out.println("[ItemMeta] Universe class = " + universe.getClass().getName());
                
                // Look for translation-related methods
                for (java.lang.reflect.Method m : universe.getClass().getMethods()) {
                    String mName = m.getName().toLowerCase();
                    if (mName.contains("trans") || mName.contains("lang") || mName.contains("local") || mName.contains("resolve")) {
                        System.out.println("[ItemMeta]   Universe." + m.getName() + "(" + m.getParameterCount() + " args) -> " + m.getReturnType().getSimpleName());
                    }
                }
                
                // Try getServerContext
                try {
                    Object serverContext = universe.getClass().getMethod("getServerContext").invoke(universe);
                    if (serverContext != null) {
                        System.out.println("[ItemMeta] ServerContext class = " + serverContext.getClass().getName());
                        for (java.lang.reflect.Method m : serverContext.getClass().getMethods()) {
                            String mName = m.getName().toLowerCase();
                            if (mName.contains("trans") || mName.contains("lang") || mName.contains("local") || mName.contains("resolve")) {
                                System.out.println("[ItemMeta]   ServerContext." + m.getName() + "(" + m.getParameterCount() + " args) -> " + m.getReturnType().getSimpleName());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } catch (Exception e) {
                System.out.println("[ItemMeta] Error accessing Universe: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("[ItemMeta] Debug error: " + e.getMessage());
        }
    }
}
