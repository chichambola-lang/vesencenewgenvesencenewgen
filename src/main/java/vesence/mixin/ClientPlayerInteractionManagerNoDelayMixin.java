package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.movement.NoDelay;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerNoDelayMixin {

    @Shadow
    private int blockBreakingCooldown;

    @Inject(method = {"updateBlockBreakingProgress"}, at = {@At("HEAD")})
    private void onUpdateBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Vesence.isModInitialized()) {
            try {
                NoDelay module = Vesence.get.manager.get(NoDelay.class);
                if (module != null && module.enable && NoDelay.blocks.get()) {
                    blockBreakingCooldown = 0;
                }
            } catch (Exception ignored) {}
        }
    }
}
