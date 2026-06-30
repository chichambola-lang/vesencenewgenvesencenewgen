package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.text.FontRegistry;

@IModule(name = "Better Minecraft", description = "Улучшения ванильного интерфейса", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class BetterMinecraft extends Module {
   public static final BooleanSetting customButtons = new BooleanSetting("Кастомные кнопки", true);

   private static BetterMinecraft instance;

   public BetterMinecraft() {
      this.addSettings(customButtons);
      instance = this;
   }

   public static boolean customButtonsEnabled() {
      return instance != null && instance.enable && customButtons.get();
   }

   public static boolean renderButton(ClickableWidget widget) {

      if (widget == null) {
         return false;
      }
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.getWindow() == null) {
         return false;
      }
      int fbW = client.getWindow().getFramebufferWidth();
      int fbH = client.getWindow().getFramebufferHeight();
      if (fbW <= 0 || fbH <= 0) {
         return false;
      }
      double scale = client.getWindow().getScaleFactor();

      float bx = (float) (widget.getX() * scale);
      float by = (float) (widget.getY() * scale);
      float bw = (float) (widget.getWidth() * scale);
      float bh = (float) (widget.getHeight() * scale);
      float rounding = (float) (11 * scale);

      boolean hovered = widget.isHovered();
      float widgetAlpha = widget.getAlpha();

      int bgAlpha = (int) ((hovered ? 145 : 120) * widgetAlpha);
      int fillColor = ColorUtil.getColor(0, 0, 0, bgAlpha);
      int textAlpha = (int) ((widget.active ? 255 : 150) * widgetAlpha);

      GlState.Snapshot snapshot = GlState.push();
      Framebuffer mainFb = client.getFramebuffer();
      int tempFbo = 0;
      try {
         if (mainFb != null && mainFb.getColorAttachment() instanceof GlTexture glColor) {
            tempFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glColor.getGlId(), 0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
               GL30.glDeleteFramebuffers(tempFbo);
               tempFbo = 0;
            }
         } else {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
         }

         Vesence.ensureRendererInitialized();
         Renderer2D renderer = Vesence.getRenderer();
         if (renderer != null) {
            renderer.begin(fbW, fbH);
            try {
               if (bgAlpha > 2) {
                  renderer.blur(bx, by, bw, bh, 18, 9, widgetAlpha);
               }
               renderer.rect(bx, by, bw, bh, 9, fillColor);

               String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
               float fontSize = (float) (15.0 * scale);
               renderer.textCenter(FontRegistry.MONTSERRAT, bx + bw / 2f, by + bh / 2f + fontSize / 4f,
                     fontSize, text, ColorUtil.replAlpha(-1, textAlpha));
            } finally {
               renderer.end();
            }
         }
      } catch (Exception e) {
         System.err.println("[Vesence] BetterButton render error: " + e.getMessage());
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
      return true;
   }
}
