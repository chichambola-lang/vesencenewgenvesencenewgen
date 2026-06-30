package vesence.ui.clickgui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.renderengine.render.Renderer2D;
import vesence.ui.clickgui.GuiScreen;
import vesence.ui.clickgui.compact.CompactGuiRender;
import vesence.ui.clickgui.compact.CompactGuiScreen;
import vesence.utils.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiRender extends GuiScreen {
   public static void render(Renderer2D renderer2D, DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getWindow() != null) {
         int viewportWidth = client.getWindow().getFramebufferWidth();
         int viewportHeight = client.getWindow().getFramebufferHeight();
         if (viewportWidth > 0 && viewportHeight > 0) {
            float actualScale = ScaleHelper.getScale();
            int mouseX = (int)(p_281550_ / actualScale);
            int mouseY = (int)(p_282878_ / actualScale);

            CompactGuiScreen.currentMouseX = (int) CompactGuiScreen.toGuiX(mouseX);
            CompactGuiScreen.currentMouseY = (int) CompactGuiScreen.toGuiY(mouseY);
            GuiScreen.currentMouseX = mouseX;
            GuiScreen.currentMouseY = mouseY;

            GuiScreen.alphaPC.update();
            GuiScreen.settingPC.update();
            GuiScreen.alphaPC2.update();
            GuiScreen.alphaPC3.update();
            if (GuiScreen.categories != null) {
               for (Category cat : GuiScreen.categories) {
                  GuiScreen.getPanelScaleAnimation(cat).update();
                  for (Module module : Vesence.get.manager.getType(cat)) {
                     GuiScreen.getModuleSettingsAnimation(module).update();
                     GuiScreen.getModuleSettingsAlphaAnimation(module).update();
                     GuiScreen.getModuleBindAnimation(module).update();
                  }
               }
            }

            GuiScreen.alpha.run(1.0);
            float mainAlpha = (float)GuiScreen.alphaPC.getValue();
            if (mainAlpha > 0.001F) {
               float scale = (float)client.getWindow().getFramebufferWidth() / client.getWindow().getScaledWidth();
               renderer2D.pushScale(scale);
               try {
                  CompactGuiRender.render(renderer2D, mouseX, mouseY, mainAlpha * (float)categoryAnimation.getOutput());
               } finally {
                  renderer2D.popTransform();
               }
            }
         }
      }
   }
}
