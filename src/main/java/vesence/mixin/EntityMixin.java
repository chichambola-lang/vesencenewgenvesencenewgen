package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.player.EventOnMovePost;

@Environment(EnvType.CLIENT)
@Mixin({ Entity.class })
public abstract class EntityMixin {
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void onIsInvisibleTo(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        if (Vesence.isModInitialized() && Vesence.get.manager.get(vesence.module.impl.visuals.SeeInvisibles.class).enable) {
            Entity self = (Entity) (Object) this;
            if (self instanceof PlayerEntity && self != net.minecraft.client.MinecraftClient.getInstance().player) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = { "getTargetingMargin" }, at = { @At("RETURN") }, cancellable = true)
    private void client$getTargetingMargin(CallbackInfoReturnable<Float> cir) {

    }

    @Inject(method = "updateVelocity", at = @At("TAIL"))
    private void onUpdateVelocity(float speed, Vec3d movementInput, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (MinecraftClient.getInstance().player != null && self.getId() == MinecraftClient.getInstance().player.getId()) {
            EventManager.call(new EventOnMovePost(speed, movementInput));
        }
    }
}
