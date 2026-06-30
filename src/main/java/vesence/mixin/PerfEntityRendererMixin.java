package vesence.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.performance.OcclusionCulling;
import vesence.module.impl.performance.RenderOptimizer;

@Mixin(EntityRenderer.class)
public abstract class PerfEntityRendererMixin {

   @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
   private void vesence$perfCull(Entity entity, net.minecraft.client.render.Frustum frustum,
                                 double cameraX, double cameraY, double cameraZ,
                                 CallbackInfoReturnable<Boolean> cir) {
      if (entity == null) {
         return;
      }
      MinecraftClient mc = MinecraftClient.getInstance();

      if (mc != null && entity == mc.player) {
         return;
      }

      double ex = entity.getX();
      double ey = entity.getY() + entity.getHeight() * 0.5;
      double ez = entity.getZ();

      double dx = ex - cameraX;
      double dy = ey - cameraY;
      double dz = ez - cameraZ;
      double distSq = dx * dx + dy * dy + dz * dz;

      if (RenderOptimizer.shouldCull(distSq)) {
         cir.setReturnValue(false);
         return;
      }

      if (OcclusionCulling.isOutsideView(entity, ex, ey, ez, cameraX, cameraY, cameraZ)) {
         cir.setReturnValue(false);
      }
   }
}
