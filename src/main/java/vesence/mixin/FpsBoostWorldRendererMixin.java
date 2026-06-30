package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.performance.FpsBoost;

@Environment(EnvType.CLIENT)
@Mixin(WorldRenderer.class)
public class FpsBoostWorldRendererMixin {

   @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
   private void vesence$skipSky(CallbackInfo ci) {
      if (FpsBoost.SKIP_SKY.get()) {
         ci.cancel();
      }
   }
}
