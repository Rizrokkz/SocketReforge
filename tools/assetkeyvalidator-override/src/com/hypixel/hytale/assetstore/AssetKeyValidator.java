package com.hypixel.hytale.assetstore;

import java.util.function.Supplier;

import com.hypixel.hytale.codec.schema.SchemaContext;
import com.hypixel.hytale.codec.schema.config.Schema;
import com.hypixel.hytale.codec.validation.ValidationResults;
import com.hypixel.hytale.codec.validation.Validator;

/**
 * Override for AssetKeyValidator to tolerate empty droplist keys during decode.
 *
 * This preserves existing behavior for all other assets.
 */
public class AssetKeyValidator<K> implements Validator<K> {
    public static final boolean PATCH_ACTIVE = true;
    public static final String PATCH_VERSION = "droplist-null-v1";

    private final Supplier<AssetStore<K, ?, ?>> store;

    public AssetKeyValidator(Supplier<AssetStore<K, ?, ?>> store) {
        this.store = store;
    }

    public AssetStore<K, ?, ?> getStore() {
        return store.get();
    }

    @Override
    public void accept(K key, ValidationResults results) {
        AssetStore<K, ?, ?> resolved = store.get();
        if (key instanceof String str && str.isBlank()) {
            Class<?> assetClass = resolved.getAssetClass();
            if (assetClass != null
                    && "com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList".equals(assetClass.getName())) {
                return; // tolerate empty droplist ids
            }
        }
        resolved.validate(key, results, results.getExtraInfo());
    }

    @Override
    public void updateSchema(SchemaContext context, Schema schema) {
        schema.setHytaleAssetRef(store.get().getAssetClass().getSimpleName());
    }
}
