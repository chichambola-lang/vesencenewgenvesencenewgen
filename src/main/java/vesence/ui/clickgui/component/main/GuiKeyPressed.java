package vesence.ui.clickgui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.ui.clickgui.GuiScreen;
import vesence.utils.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class GuiKeyPressed extends GuiScreen {

   private static Module lastBoundModule = null;
   private static long lastBoundModuleTime = 0L;
   private static BindSettings lastBoundBind = null;
   private static long lastBoundBindTime = 0L;

   public static boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      long comboNow = System.currentTimeMillis();
      // Дозахват комбо для модуля: пока открыто окно 700ms после назначения
      // основного бинда, каждое следующее нажатие добавляется в comboKeys.
      if (GuiScreen.activeModuleBind == null && lastBoundModule != null
              && comboNow - lastBoundModuleTime < 700L
              && keyCode != 256 && keyCode != 261
              && keyCode != lastBoundModule.bind
              && !lastBoundModule.comboKeys.contains(keyCode)) {
         lastBoundModule.comboKeys.add(keyCode);
         lastBoundModuleTime = comboNow; // продлеваем окно для следующей клавиши
         if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         return true;
      }
      // Дозахват комбо для BindSettings (предметы Assist).
      if (GuiScreen.activeBindSetting == null && lastBoundBind != null
              && comboNow - lastBoundBindTime < 700L
              && keyCode != 256 && keyCode != 261
              && keyCode != lastBoundBind.key
              && !lastBoundBind.extraKeys.contains(keyCode)) {
         lastBoundBind.extraKeys.add(keyCode);
         lastBoundBindTime = comboNow;
         if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         return true;
      }
      if (GuiScreen.activeModuleBind != null) {
         if (keyCode == 256) {
            GuiScreen.activeModuleBind.binding = false;
            GuiScreen.activeModuleBind = null;
         } else if (keyCode == 261) {
            GuiScreen.activeModuleBind.bind = -1;
            GuiScreen.activeModuleBind.comboKeys.clear();
            GuiScreen.activeModuleBind.binding = false;
            GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(0.0, 0.2, Easings.SINE_OUT);
            GuiScreen.activeModuleBind = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         } else {
            GuiScreen.activeModuleBind.bind = keyCode;
            GuiScreen.activeModuleBind.comboKeys.clear();
            GuiScreen.activeModuleBind.binding = false;
            GuiScreen.getModuleBindAnimation(GuiScreen.activeModuleBind).run(1.0, 0.2, Easings.SINE_OUT);
            lastBoundModule = GuiScreen.activeModuleBind;
            lastBoundModuleTime = comboNow;
            GuiScreen.activeModuleBind = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         }

         return true;
      } else if (GuiScreen.activeBindSetting != null) {
         if (keyCode == 256) {
            GuiScreen.activeBindSetting.active = false;
            GuiScreen.activeBindSetting = null;
         } else if (keyCode == 261) {
            GuiScreen.activeBindSetting.key = -1;
            GuiScreen.activeBindSetting.extraKeys.clear();
            GuiScreen.activeBindSetting.active = false;
            GuiScreen.activeBindSetting = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         } else {
            GuiScreen.activeBindSetting.key = keyCode;
            GuiScreen.activeBindSetting.extraKeys.clear();
            GuiScreen.activeBindSetting.active = false;
            lastBoundBind = GuiScreen.activeBindSetting;
            lastBoundBindTime = comboNow;
            GuiScreen.activeBindSetting = null;
            if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
         }

         return true;
      } else {
         if (GuiScreen.activeStringSetting != null) {
            if (keyCode == 256) {
               GuiScreen.activeStringSetting.active = false;
               GuiScreen.activeStringSetting = null;
               vesence.utils.player.MovementManager.getInstance().unlockMovement("StringSetting");
               if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();

               return true;
            }

            if (keyCode == 259) {
               if (!GuiScreen.activeStringSetting.input.isEmpty()) {
                  GuiScreen.activeStringSetting.input = GuiScreen.activeStringSetting.input.substring(0, GuiScreen.activeStringSetting.input.length() - 1);
                  if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
               }

               return true;
            }
         }

         if (GuiScreen.activeSearch) {
            if (keyCode == 256) {
               GuiScreen.activeSearch = false;
               GuiScreen.searchText = "";
               return true;
            }

            if (keyCode == 259) {
               return true;
            }
         }

         return false;
      }
   }
}
