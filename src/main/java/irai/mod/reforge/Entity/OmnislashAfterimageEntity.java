package irai.mod.reforge.Entity;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSettings;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PropComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** A short-lived, non-interactive snapshot of a player's rendered model. */
public final class OmnislashAfterimageEntity extends Entity {
    public static final BuilderCodec<OmnislashAfterimageEntity> CODEC =
            BuilderCodec.builder(OmnislashAfterimageEntity.class, OmnislashAfterimageEntity::new, Entity.CODEC).build();

    private Model snapshotModel;
    private ActiveAnimationComponent snapshotAnimation;
    private MovementStatesComponent snapshotMovement;
    private PlayerSkinComponent snapshotSkin;
    private PlayerSettings snapshotPlayerSettings;
    private InventoryComponent.Armor snapshotArmor;
    private InventoryComponent.Hotbar snapshotHotbar;
    private InventoryComponent.Utility snapshotUtility;

    public OmnislashAfterimageEntity() {
        super();
    }

    public OmnislashAfterimageEntity(World world) {
        super(world);
    }

    public OmnislashAfterimageEntity(World world,
                                     Model model,
                                     ActiveAnimationComponent animation,
                                     MovementStatesComponent movement,
                                     PlayerSkinComponent skin,
                                     PlayerSettings playerSettings,
                                     InventoryComponent.Armor armor,
                                     InventoryComponent.Hotbar hotbar,
                                     InventoryComponent.Utility utility) {
        super(world);
        this.snapshotModel = model == null ? null : new Model(model);
        this.snapshotAnimation = animation == null
                ? null
                : new ActiveAnimationComponent(animation.getActiveAnimations());
        this.snapshotMovement = movement == null ? null : new MovementStatesComponent(movement);
        this.snapshotSkin = skin == null || skin.getPlayerSkin() == null
                ? null
                : new PlayerSkinComponent(new PlayerSkin(skin.getPlayerSkin()));
        this.snapshotPlayerSettings = playerSettings == null
                ? null
                : (PlayerSettings) playerSettings.clone();
        this.snapshotArmor = armor == null ? null : (InventoryComponent.Armor) armor.clone();
        this.snapshotHotbar = hotbar == null ? null : (InventoryComponent.Hotbar) hotbar.clone();
        this.snapshotUtility = utility == null ? null : (InventoryComponent.Utility) utility.clone();
    }

    @Override
    public Holder<EntityStore> toHolder() {
        Holder<EntityStore> holder = super.toHolder();
        if (snapshotModel == null) {
            return holder;
        }

        holder.putComponent(ModelComponent.getComponentType(), new ModelComponent(new Model(snapshotModel)));
        holder.putComponent(BoundingBox.getComponentType(), new BoundingBox(snapshotModel.getBoundingBox()));
        holder.putComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.putComponent(PropComponent.getComponentType(), PropComponent.get());
        if (snapshotAnimation != null) {
            holder.putComponent(ActiveAnimationComponent.getComponentType(),
                    new ActiveAnimationComponent(snapshotAnimation.getActiveAnimations()));
        }
        if (snapshotMovement != null) {
            holder.putComponent(MovementStatesComponent.getComponentType(),
                    new MovementStatesComponent(snapshotMovement));
        }
        if (snapshotSkin != null) {
            holder.putComponent(PlayerSkinComponent.getComponentType(),
                    new PlayerSkinComponent(new PlayerSkin(snapshotSkin.getPlayerSkin())));
        }
        if (snapshotPlayerSettings != null) {
            holder.putComponent(PlayerSettings.getComponentType(), (PlayerSettings) snapshotPlayerSettings.clone());
        }
        if (snapshotArmor != null) {
            holder.putComponent(InventoryComponent.Armor.getComponentType(),
                    (InventoryComponent.Armor) snapshotArmor.clone());
        }
        if (snapshotHotbar != null) {
            holder.putComponent(InventoryComponent.Hotbar.getComponentType(),
                    (InventoryComponent.Hotbar) snapshotHotbar.clone());
        }
        if (snapshotUtility != null) {
            holder.putComponent(InventoryComponent.Utility.getComponentType(),
                    (InventoryComponent.Utility) snapshotUtility.clone());
        }
        return holder;
    }
}
