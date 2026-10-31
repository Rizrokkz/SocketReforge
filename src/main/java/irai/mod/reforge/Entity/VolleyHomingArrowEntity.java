package irai.mod.reforge.Entity;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Non-interactive arrow model used while Volley arrows gather and home. */
public final class VolleyHomingArrowEntity extends Entity {
    private static final String[] MODEL_ASSET_IDS = {
            "Arrow_Iron",
            "Arrow",
            "Arrow_Projectile",
            "Projectile_Arrow",
            "Items/Projectiles/Arrow"
    };
    private static final float MODEL_SCALE = 0.7f;

    public static final BuilderCodec<VolleyHomingArrowEntity> CODEC =
            BuilderCodec.builder(VolleyHomingArrowEntity.class,
                    VolleyHomingArrowEntity::new, Entity.CODEC).build();

    private Model arrowModel;

    public VolleyHomingArrowEntity() {
        super();
    }

    public VolleyHomingArrowEntity(World world) {
        super(world);
        var assets = ModelAsset.getAssetMap();
        if (assets == null) {
            return;
        }
        for (String assetId : MODEL_ASSET_IDS) {
            ModelAsset asset = assets.getAsset(assetId);
            if (asset != null) {
                arrowModel = Model.createStaticScaledModel(asset, MODEL_SCALE);
                break;
            }
        }
    }

    @Override
    public Holder<EntityStore> toHolder() {
        Holder<EntityStore> holder = super.toHolder();
        if (arrowModel == null) {
            return holder;
        }
        Model model = new Model(arrowModel);
        holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.putComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.putComponent(PropComponent.getComponentType(), PropComponent.get());
        return holder;
    }

    public boolean hasModel() {
        return arrowModel != null;
    }
}
