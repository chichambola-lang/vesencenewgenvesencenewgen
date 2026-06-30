package vesence.ui;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.module.impl.performance.Intensity;
import vesence.module.impl.performance.PerformanceModule;

@Environment(EnvType.CLIENT)
public class PerfSettingsScreen extends Screen {

   private static final int ROW_HEIGHT = 24;
   private static final int TOGGLE_WIDTH = 120;
   private static final int INTENSITY_WIDTH = 110;
   private static final int GAP = 6;
   private static final int LABEL_WIDTH = 150;

   private final Screen parent;
   private final List<PerformanceModule> modules = new ArrayList<>();

   public PerfSettingsScreen(Screen parent) {
      super(Text.literal("Vesence — Производительность"));
      this.parent = parent;
   }

   private void collectModules() {
      modules.clear();
      Vesence v = Vesence.get;
      if (v != null && v.manager != null) {
         for (Module m : v.manager.getModules()) {
            if (m instanceof PerformanceModule pm) {
               modules.add(pm);
            }
         }
      }
   }

   @Override
   protected void init() {
      super.init();
      collectModules();

      int totalRowWidth = LABEL_WIDTH + GAP + TOGGLE_WIDTH + GAP + INTENSITY_WIDTH;
      int startX = this.width / 2 - totalRowWidth / 2;
      int topY = 40;

      for (int i = 0; i < modules.size(); i++) {
         final PerformanceModule pm = modules.get(i);
         int rowY = topY + i * (ROW_HEIGHT + 4);

         int toggleX = startX + LABEL_WIDTH + GAP;
         int intensityX = toggleX + TOGGLE_WIDTH + GAP;

         CyclingButtonWidget<Boolean> enableButton = CyclingButtonWidget
               .onOffBuilder(pm.enable)
               .build(toggleX, rowY, TOGGLE_WIDTH, ROW_HEIGHT - 4, Text.literal(pm.name),
                     (button, value) -> {
                        if (pm.enable != value) {
                           pm.toggle();
                        }
                     });
         this.addDrawableChild(enableButton);

         CyclingButtonWidget<Intensity> intensityButton = CyclingButtonWidget
               .<Intensity>builder(in -> Text.literal(in.name()), pm.intensity())
               .values(Intensity.values())
               .omitKeyText()
               .build(intensityX, rowY, INTENSITY_WIDTH, ROW_HEIGHT - 4, Text.literal("Интенсивность"),
                     (button, value) -> {
                        pm.intensitySetting().currentMode = value.name();
                        if (Vesence.get != null && Vesence.get.configManager != null) {
                           Vesence.get.configManager.autoSave();
                        }
                     });
         this.addDrawableChild(intensityButton);
      }

      this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> this.close())
            .dimensions(this.width / 2 - 100, this.height - 28, 200, 20)
            .build());
   }

   @Override
   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);

      context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 16, 0xFFFFFFFF);

      int totalRowWidth = LABEL_WIDTH + GAP + TOGGLE_WIDTH + GAP + INTENSITY_WIDTH;
      int startX = this.width / 2 - totalRowWidth / 2;
      int topY = 40;
      for (int i = 0; i < modules.size(); i++) {
         PerformanceModule pm = modules.get(i);
         int rowY = topY + i * (ROW_HEIGHT + 4) + (ROW_HEIGHT - 4) / 2 - this.textRenderer.fontHeight / 2;
         context.drawTextWithShadow(this.textRenderer, Text.literal(pm.name), startX, rowY, 0xFFFFFFFF);
      }
   }

   @Override
   public void close() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc != null) {
         mc.setScreen(parent);
      }
   }

   @Override
   public boolean shouldPause() {
      return true;
   }
}
