package vesence.module.impl.visuals;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.world.World;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.mods.particular.ParticularWaterSplash;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

@IModule(name = "Particular", description = "Брызги воды при падении в воду", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ParticularWater extends Module {

   private final ParticularWaterSplash splash = new ParticularWaterSplash();
   private final Map<UUID, Boolean> itemWasInWater = new HashMap<>();
   private final Map<UUID, Deque<Float>> itemVelocities = new HashMap<>();

   private boolean wasInWater;
   private World trackedWorld;

   @Override
   public void onDisable() {
      resetState();
      super.onDisable();
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.world == null) {
         resetState();
         return;
      }

      if (trackedWorld != mc.world) {
         resetState();
         trackedWorld = mc.world;
      }

      ClientPlayerEntity player = mc.player;
      splash.trackVelocity(player);

      boolean inWater = player.isTouchingWater() || player.isSubmergedInWater();
      if (inWater && !wasInWater) {
         splash.trySpawnOnWaterEntry(player);
      }
      wasInWater = inWater;

      for (Entity entity : mc.world.getOtherEntities(null, player.getBoundingBox().expand(64.0D), e -> e instanceof ItemEntity)) {
         ItemEntity item = (ItemEntity) entity;
         UUID uuid = item.getUuid();
         boolean currentlyInWater = item.isTouchingWater() || item.isSubmergedInWater();
         boolean wasIn = itemWasInWater.getOrDefault(uuid, false);

         Deque<Float> velocities = itemVelocities.computeIfAbsent(uuid, key -> new ArrayDeque<>(4));
         velocities.addLast((float) Math.abs(item.getVelocity().y));
         if (velocities.size() > 4) {
            velocities.removeFirst();
         }

         if (currentlyInWater && !wasIn) {
            float speed = velocities.isEmpty() ? 0.0F : Collections.max(velocities);
            ParticularWaterSplash.spawnEmitter(item.getEntityWorld(), item.getX(), item.getY(), item.getZ(), item.getWidth() * 2.0F, speed);
         }

         itemWasInWater.put(uuid, currentlyInWater);
      }
   }

   private void resetState() {
      wasInWater = false;
      trackedWorld = null;
      itemWasInWater.clear();
      itemVelocities.clear();
   }
}
