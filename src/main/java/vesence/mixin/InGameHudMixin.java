package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.render.EventScreen;
import vesence.event.render.EventScreenPre;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.NoRender;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.animation.util.AnimationSystem;
import vesence.utils.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public class InGameHudMixin {

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized() && Vesence.get.manager.get(vesence.module.impl.visuals.Crosshair.class).enable) {
         ci.cancel();
      }
   }

   @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
   private void onRenderScoreboard(CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Таблица")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
   private void onRenderTitle(CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Тайтлы")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderVignette(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Виньетка")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderStatusEffectOverlay(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Иконки эффектов")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderNausea(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Тошнота")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderPortal(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Портал на экране")) {
         ci.cancel();
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void onPreRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null && client.getWindow() != null) {
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            if (width > 0 && height > 0) {
               GlState.Snapshot snapshot = GlState.push();

               Framebuffer mainFramebuffer = client.getFramebuffer();
               int tempFbo = 0;
               if (mainFramebuffer != null) {
                  if (mainFramebuffer.getColorAttachment() instanceof GlTexture glColor) {
                     int mainFramebufferTextureId = glColor.getGlId();
                     tempFbo = GL30.glGenFramebuffers();
                     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                     GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainFramebufferTextureId, 0);
                     GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                     int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                     if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                        GL30.glDeleteFramebuffers(tempFbo);
                        tempFbo = 0;
                     }
                  } else {
                     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                  }
               } else {
                  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
               }

               try {
                  Vesence.ensureRendererInitialized();
                  Renderer2D renderer = Vesence.getRenderer();
                  if (renderer != null) {
                     renderer.begin(width, height);
                     try {
                        EventManager.call(new EventScreenPre(client, renderer, FontRegistry.SF_MEDIUM, width, height, context));
                     } finally {
                        renderer.end();
                     }
                  }
               } catch (Exception e) {
                  System.err.println("[Vesence] Pre-HUD render error: " + e.getMessage());
               } finally {
                  if (tempFbo != 0) {
                     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                     GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
                  }
                  GlState.pop(snapshot);
                  if (tempFbo != 0) {
                     GL30.glDeleteFramebuffers(tempFbo);
                  }
               }
            }
         }
      }
   }

   @Inject(method = "render", at = @At("RETURN"))
   private void onRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null && client.getWindow() != null) {
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            if (width > 0 && height > 0) {
               GlState.Snapshot snapshot = GlState.push();

               Framebuffer mainFramebuffer = client.getFramebuffer();
               int tempFbo = 0;
               if (mainFramebuffer != null) {
                  if (mainFramebuffer.getColorAttachment() instanceof GlTexture glColor) {
                     int mainFramebufferTextureId = glColor.getGlId();
                     tempFbo = GL30.glGenFramebuffers();
                     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                     GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, mainFramebufferTextureId, 0);
                     GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
                     int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
                     if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                        GL30.glDeleteFramebuffers(tempFbo);
                        tempFbo = 0;
                     }
                  } else {
                     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
                  }
               } else {
                  GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
               }

               try {
                  Vesence.ensureRendererInitialized();
                  Renderer2D renderer = Vesence.getRenderer();
                  if (renderer != null) {
                     AnimationSystem.getInstance().tick();
                     renderer.begin(width, height);
                     try {
                        EventManager.call(new EventScreen(client, renderer, FontRegistry.SF_MEDIUM, width, height, context));
                     } finally {
                        renderer.end();
                     }
                  }
               } catch (Exception e) {
                  System.err.println("[Vesence] HUD render error: " + e.getMessage());
               } finally {
                  if (tempFbo != 0) {
                     GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
                     GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
                  }
                  GlState.pop(snapshot);
                  if (tempFbo != 0) {
                     GL30.glDeleteFramebuffers(tempFbo);
                  }
               }
            }
         }
      }
   }
}
