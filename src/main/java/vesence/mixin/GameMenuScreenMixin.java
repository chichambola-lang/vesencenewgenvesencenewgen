package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.ui.PerfSettingsScreen;

@Environment(EnvType.CLIENT)
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {

   protected GameMenuScreenMixin(Text title) {
      super(title);
   }

   @Inject(method = "init", at = @At("TAIL"))
   private void vesence$addPerfButton(CallbackInfo ci) {
      GameMenuScreen self = (GameMenuScreen) (Object) this;
      if (!self.shouldShowMenu()) {
         return;
      }
      int w = 150;
      int h = 20;
      int x = 6;
      int y = this.height - h - 6;
      ButtonWidget button = ButtonWidget.builder(
            Text.literal("Vesence: Производительность"),
            b -> {
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc != null) {
                  mc.setScreen(new PerfSettingsScreen(self));
               }
            }
      ).dimensions(x, y, w, h).build();
      this.addDrawableChild(button);
   }
}
