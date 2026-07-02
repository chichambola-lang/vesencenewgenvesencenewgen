package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

/**
 * Скрывает тени сущностей: обнуляем радиус тени, из-за чего ванильный рендер
 * теней пропускается.
 */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public class NoRenderEntityRendererMixin {
    @Inject(method = "getShadowRadius", at = @At("HEAD"), cancellable = true)
    private void vesence$hideShadow(EntityRenderState state, CallbackInfoReturnable<Float> cir) {
        if (!Vesence.isModInitialized()) return;
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Тени")) {
            cir.setReturnValue(0.0F);
        }
    }
}
