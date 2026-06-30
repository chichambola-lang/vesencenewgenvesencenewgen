package vesence.mods.particular;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class ParticularWaterSplash {
   private final Deque<Float> velocities = new ArrayDeque<>(4);

   public void trackVelocity(ClientPlayerEntity player) {
      if (player == null) return;
      velocities.addLast((float) Math.abs(player.getVelocity().y));
      if (velocities.size() > 4) {
         velocities.removeFirst();
      }
   }

   public void trySpawnOnWaterEntry(ClientPlayerEntity player) {
      if (player == null) return;
      World world = player.getEntityWorld();
      if (world == null || !world.isClient()) return;

      double surfaceY = findWaterSurfaceY(player);
      float speed = velocities.isEmpty() ? 0.0F : Collections.max(velocities);
      spawnEmitter(world, player.getX(), surfaceY, player.getZ(), player.getWidth() * 2.0F, speed);
   }

   private double findWaterSurfaceY(ClientPlayerEntity player) {
      World world = player.getEntityWorld();
      BlockPos basePos = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());

      for (int i = 0; i < 5; i++) {
         BlockPos pos = basePos.up(i);
         FluidState fluidState = world.getFluidState(pos);
         if (fluidState.isIn(FluidTags.WATER)) {
            return pos.getY() + fluidState.getHeight(world, pos) - 0.01D;
         }
      }
      return player.getY();
   }

   public static void spawnEmitter(World world, double x, double y, double z, float width, float speed) {
      if (world == null || !world.isClient()) return;
      world.addParticleClient(ParticularParticleTypes.WATER_SPLASH_EMITTER, x, y, z, width, speed, 0.0D);
   }
}
