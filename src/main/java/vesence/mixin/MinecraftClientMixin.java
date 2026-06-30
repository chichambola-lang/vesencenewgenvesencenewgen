package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.impl.EventChangeWorld;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.ui.MainScreen;
import vesence.ui.MainScreenHelper;

@Environment(EnvType.CLIENT)
@Mixin({ MinecraftClient.class })
public abstract class MinecraftClientMixin {

   @Inject(method = { "tick" }, at = { @At("TAIL") })
   private void publishClientTick(CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         MinecraftClient client = (MinecraftClient) (Object) this;
         if (!client.isPaused()) {
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;
            if (player != null && world != null) {
               EventManager.call(new ClientTickEvent(client));
            }
         }
      }
   }
   @Inject(method = { "joinWorld" }, at = { @At("TAIL") })
   public void loadWorld(CallbackInfo ci) {
      EventManager.call(new EventChangeWorld());
   }

   @Inject(method = { "setScreen" }, at = { @At("HEAD") }, cancellable = true, require = 0, expect = 0)
   private void vesence$redirectTitleScreen(Screen screen, CallbackInfo ci) {
      if (screen instanceof TitleScreen) {
         if (MainScreenHelper.bypassOnce) {
            MainScreenHelper.bypassOnce = false;
            return;
         }
         MinecraftClient mc = (MinecraftClient) (Object) this;
         ci.cancel();
         mc.setScreen(new MainScreen());
         return;
      }

      if (screen instanceof MultiplayerScreen && !(screen instanceof vesence.ui.MultiplayerScreen)) {
         if (MainScreenHelper.bypassOnce) {
            MainScreenHelper.bypassOnce = false;
            return;
         }
         MinecraftClient mc = (MinecraftClient) (Object) this;
         ci.cancel();
         boolean ru = false;
         try { ru = new vesence.ui.SettingsWindow().getSelectedLangMode() == 0; } catch (Throwable ignored) {}
         mc.setScreen(new vesence.ui.MultiplayerScreen(new MainScreen()).setRussian(ru));
      }
   }

   @Inject(method = { "onResolutionChanged" }, at = { @At("TAIL") })
   private void vesence$onResolutionChanged(CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         try {
            vesence.renderengine.render.Renderer2D.clearImageTextureCache();
         } catch (Exception ignored) {
         }
      }
   }

   @Inject(method = { "reloadResources()Ljava/util/concurrent/CompletableFuture;" }, at = { @At("HEAD") })
   private void vesence$onReloadResources(CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> cir) {
      if (Vesence.isModInitialized()) {
         try {
            vesence.renderengine.render.Renderer2D.clearImageTextureCache();
         } catch (Exception ignored) {
         }
      }
   }
}
