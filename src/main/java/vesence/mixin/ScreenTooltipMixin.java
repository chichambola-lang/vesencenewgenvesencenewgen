package vesence.mixin;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.misc.AuctionHelper;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenTooltipMixin {

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"))
    private static void onGetTooltip(MinecraftClient client, ItemStack stack, CallbackInfoReturnable<List<Text>> cir) {
        AuctionHelper.appendPerUnitTooltip(stack, cir.getReturnValue());
    }
}
