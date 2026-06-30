package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(ParticleManager.class)
public class NoRenderParticleManagerMixin {
    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V", at = @At("HEAD"), cancellable = true)
    private void onAddEmitter(net.minecraft.entity.Entity entity, net.minecraft.particle.ParticleEffect parameters, int maxAge, CallbackInfo ci) {
        if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Партиклы тотема") && parameters.getType() == net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING) {
            ci.cancel();
        }
    }
}
