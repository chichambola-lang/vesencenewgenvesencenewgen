package vesence.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import net.minecraft.client.sound.Channel.SourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin({SoundSystem.class})
public abstract class SoundSystemMixin {
   @Shadow
   private Map<SoundInstance, SourceManager> sources;

    @Inject(
        method = {"play"},
        at = {@At("RETURN")}
    )
    private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<PlayResult> cir) {

    }
}
