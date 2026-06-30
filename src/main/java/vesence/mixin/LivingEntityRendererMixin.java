package vesence.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.Nametags;
import vesence.module.impl.visuals.SeeInvisibles;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;mix(II)I"))
    private int onColorMix(int j, int mixColor) {
        if (j == 0x26FFFFFF) {
            if (Vesence.isModInitialized()) {
                SeeInvisibles module = Vesence.get.manager.get(SeeInvisibles.class);
                if (module != null && module.enable) {
                    int alpha = module.alpha.get().intValue();
                    j = (alpha << 24) | 0xFFFFFF;
                }
            }
        }
        return ColorHelper.mix(j, mixColor);
    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void onHasLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (Vesence.get == null || Vesence.get.manager == null) return;

        Nametags nametags = Vesence.get.manager.get(Nametags.class);
        if (nametags != null && nametags.enable && nametags.hidesVanillaLabel(entity)) {
            cir.setReturnValue(false);
        }
    }
}
