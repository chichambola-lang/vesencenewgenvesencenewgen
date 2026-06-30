package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.glfw.GLFW;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;

/**
 * Живые руки (порт мода Hold My Items).
 *
 * Управляет Lua-движком анимаций первого лица (HMI). Когда модуль выключен, все
 * миксины HMI пропускают свою логику и ванильный рендер рук работает как обычно.
 *
 * Настройки:
 *  - Тип анимации: "Дефолт" (богатые покадровые анимации HMI под каждый тип предмета)
 *    или "Взмах" (единый размашистый диагональный удар для всех предметов — реально
 *    другая механика удара).
 *  - Раздельные смещения позиции основной и второстепенной руки по X/Y/Z.
 *  - Клавиша осмотра предмета.
 */
@IModule(name = "Living Hands", description = "Анимации живых рук от первого лица (Hold My Items)", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class LivingHands extends Module {

   public static LivingHands INSTANCE;

   private final ModeSetting animationType = new ModeSetting("Тип анимации", "Дефолт", "Дефолт", "Взмах");

   private final SliderSetting mainX = new SliderSetting("Основная рука X", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting mainY = new SliderSetting("Основная рука Y", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting mainZ = new SliderSetting("Основная рука Z", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting offX = new SliderSetting("Второстепенная рука X", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting offY = new SliderSetting("Второстепенная рука Y", 0.0, -1.0, 1.0, 0.01);
   private final SliderSetting offZ = new SliderSetting("Второстепенная рука Z", 0.0, -1.0, 1.0, 0.01);

   private final BindSettings inspectKey = new BindSettings("Клавиша осмотра", GLFW.GLFW_KEY_C);

   public LivingHands() {
      INSTANCE = this;
      this.addSettings(new Setting[]{animationType, mainX, mainY, mainZ, offX, offY, offZ, inspectKey});
   }

   public static boolean isEnabled() {
      return INSTANCE != null && INSTANCE.enable;
   }

   /** 0 = Дефолт (покадровые HMI-анимации), 1 = Взмах (единый удар). */
   public static int getSwingStyle() {
      if (INSTANCE == null) return 0;
      return INSTANCE.animationType.is("Взмах") ? 1 : 0;
   }

   public static double getMainX() { return INSTANCE == null ? 0.0 : INSTANCE.mainX.get(); }
   public static double getMainY() { return INSTANCE == null ? 0.0 : INSTANCE.mainY.get(); }
   public static double getMainZ() { return INSTANCE == null ? 0.0 : INSTANCE.mainZ.get(); }
   public static double getOffX() { return INSTANCE == null ? 0.0 : INSTANCE.offX.get(); }
   public static double getOffY() { return INSTANCE == null ? 0.0 : INSTANCE.offY.get(); }
   public static double getOffZ() { return INSTANCE == null ? 0.0 : INSTANCE.offZ.get(); }

   public static int getInspectKey() {
      return INSTANCE == null ? GLFW.GLFW_KEY_C : INSTANCE.inspectKey.key;
   }
}
