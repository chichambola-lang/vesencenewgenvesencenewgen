package vesence.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.renderengine.EntityFramebufferCaptureManager;
import vesence.event.EventManager;
import vesence.event.render.EventRender3D;
import vesence.event.render.WorldRenderEvent;
import vesence.renderengine.providers.GlState;
import vesence.module.impl.visuals.Ambience;
import vesence.renderengine.render.Renderer2D;

@Environment(EnvType.CLIENT)
@Mixin({ WorldRenderer.class })
public class WorldRendererMixin {

   @Inject(method = { "render" }, at = { @At("RETURN") })
   private void publishWorldRenderEvent(
           ObjectAllocator allocator,
           RenderTickCounter tickCounter,
           boolean renderBlockOutline,
           Camera camera,
           Matrix4f positionMatrix,
           Matrix4f basicProjectionMatrix,
           Matrix4f projectionMatrix,
           GpuBufferSlice fog,
           Vector4f fogColor,
           boolean shouldRenderSky,
           CallbackInfo ci) {
      MatrixStack stack = new MatrixStack();
      Matrix4f basePositionMatrix = new Matrix4f(positionMatrix);
      stack.multiplyPositionMatrix(new Matrix4f(basePositionMatrix));

      vesence.utils.other.Mathf.lastProjMat.set(basicProjectionMatrix);
      vesence.utils.other.Mathf.lastModMat.identity();
      vesence.utils.other.Mathf.lastWorldSpaceMatrix.set(positionMatrix);

      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null) {
         GameRenderer gameRenderer = client.gameRenderer;
         if (gameRenderer != null && camera != null) {
            GlState.Snapshot snapshot = GlState.push();
            vesence.utils.render.world.WorldRenderer worldRenderer = null;

            try {
               worldRenderer = vesence.utils.render.world.WorldRenderer.begin(client, tickCounter, camera,
                       positionMatrix, projectionMatrix);
               float frameDepth = worldRenderer.tickDelta();

               try {
                  EventManager.call(new WorldRenderEvent(client, gameRenderer, worldRenderer, frameDepth));
               } finally {
                  if (worldRenderer != null) {
                     try {
                        worldRenderer.flush();
                     } finally {
                        worldRenderer.close();
                     }
                  }
               }
            } finally {
               GlState.pop(snapshot);
            }
         }
      }
      EventManager.call(new EventRender3D(stack, tickCounter.getTickProgress(true), basicProjectionMatrix, camera));
      EntityFramebufferCaptureManager.getInstance().endFrame();
   }
}
