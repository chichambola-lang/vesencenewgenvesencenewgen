package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.CameraClip;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(CallbackInfo ci) {
        if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Огонь на экране")) {
            ci.cancel();
        }
    }

    @Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void vesence$noInWallOverlay(CallbackInfoReturnable<BlockState> cir) {
        if (CameraClip.isActive()) {
            cir.setReturnValue(null);
        }
    }
}
