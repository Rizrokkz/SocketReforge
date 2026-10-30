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

/** A small, non-interactive fireball model used by Omnislash's orbiting fire projectiles. */
public final class OmnislashFireOrbEntity extends Entity {
    public static final String MODEL_ASSET_ID = "Ability_Fireball";
    public static final float MODEL_SCALE = 0.5f;
    public static final BuilderCodec<OmnislashFireOrbEntity> CODEC =
            BuilderCodec.builder(OmnislashFireOrbEntity.class, OmnislashFireOrbEntity::new, Entity.CODEC).build();

    private Model orbModel;

    public OmnislashFireOrbEntity() {
        super();
    }

    public OmnislashFireOrbEntity(World world) {
        super(world);
        ModelAsset asset = ModelAsset.getAssetMap() == null
                ? null
                : ModelAsset.getAssetMap().getAsset(MODEL_ASSET_ID);
        if (asset != null) {
            orbModel = Model.createStaticScaledModel(asset, MODEL_SCALE);
        }
    }

    @Override
    public Holder<EntityStore> toHolder() {
        Holder<EntityStore> holder = super.toHolder();
        if (orbModel == null) {
            return holder;
        }
        Model model = new Model(orbModel);
        holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(model));
        holder.putComponent(BoundingBox.getComponentType(), new BoundingBox(model.getBoundingBox()));
        holder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.putComponent(PropComponent.getComponentType(), PropComponent.get());
        return holder;
    }

    public boolean hasModel() {
        return orbModel != null;
    }
}
