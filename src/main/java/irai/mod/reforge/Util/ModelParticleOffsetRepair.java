package irai.mod.reforge.Util;

import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import org.joml.Vector3f;

import java.util.Map;

/** Repairs non-finite model particle offsets before the server serializes models to clients. */
public final class ModelParticleOffsetRepair {
    private ModelParticleOffsetRepair() {
    }

    public static void repairLoadedAssets() {
        int modelAssetCount = 0;
        int projectileCount = 0;
        int repairedParticles = 0;

        DefaultAssetMap<String, ModelAsset> models = ModelAsset.getAssetMap();
        if (models != null) {
            for (Map.Entry<String, ModelAsset> entry : models.getAssetMap().entrySet()) {
                ModelAsset asset = entry.getValue();
                if (asset == null) {
                    continue;
                }
                modelAssetCount++;
                repairedParticles += repairParticles("model", entry.getKey(), asset.getParticles());
            }
        }

        DefaultAssetMap<String, ProjectileConfig> projectiles = ProjectileConfig.getAssetMap();
        if (projectiles != null) {
            for (Map.Entry<String, ProjectileConfig> entry : projectiles.getAssetMap().entrySet()) {
                ProjectileConfig config = entry.getValue();
                if (config == null) {
                    continue;
                }
                projectileCount++;
                try {
                    Model model = config.getModel();
                    if (model != null) {
                        repairedParticles += repairParticles("projectile-model", entry.getKey(), model.getParticles());
                    }
                } catch (RuntimeException exception) {
                    System.err.println("[SocketReforge][ModelParticleRepair] Could not inspect projectile model "
                            + entry.getKey() + ": " + exception.getMessage());
                }
            }
        }

        System.out.println("[SocketReforge][ModelParticleRepair] Startup scan complete: modelAssets="
                + modelAssetCount + ", projectileConfigs=" + projectileCount
                + ", repairedParticles=" + repairedParticles + ".");
    }

    private static int repairParticles(String sourceType, String sourceId, ModelParticle[] particles) {
        if (particles == null || particles.length == 0) {
            return 0;
        }

        int repaired = 0;
        for (int index = 0; index < particles.length; index++) {
            ModelParticle particle = particles[index];
            if (particle == null) {
                continue;
            }

            Vector3f offset = particle.getPositionOffset();
            if (offset == null || (Float.isFinite(offset.x) && Float.isFinite(offset.y) && Float.isFinite(offset.z))) {
                continue;
            }

            float x = Float.isFinite(offset.x) ? offset.x : 0.0f;
            float y = Float.isFinite(offset.y) ? offset.y : 0.0f;
            float z = Float.isFinite(offset.z) ? offset.z : 0.0f;
            particle.setPositionOffset(new Vector3f(x, y, z));
            repaired++;

            System.err.println("[SocketReforge][ModelParticleRepair] Repaired non-finite PositionOffset: sourceType="
                    + sourceType + ", sourceId=" + sourceId + ", particleIndex=" + index
                    + ", systemId=" + particle.getSystemId() + ", old=" + offset
                    + ", new=(" + x + ", " + y + ", " + z + ").");
        }
        return repaired;
    }
}
