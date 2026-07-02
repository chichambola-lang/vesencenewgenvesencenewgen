package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffects;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;

@IModule(
   name = "Overlay Remover",
   description = "Убирает различные элементы отрисовки",
   category = Category.VISUALS,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoRender extends Module {

   public static final MultiBooleanSetting elements = new MultiBooleanSetting("Элементы",
           new BooleanSetting("Огонь на экране", true),
           new BooleanSetting("Лава на экране", false),
           new BooleanSetting("Вода на экране", false),
           new BooleanSetting("Оверлей в блоке", false),
           new BooleanSetting("Портал на экране", false),
           new BooleanSetting("Виньетка", false),
           new BooleanSetting("Иконки эффектов", false),
           new BooleanSetting("Тошнота", false),
           new BooleanSetting("Линия босса", true),
           new BooleanSetting("Анимация тотема", true),
           new BooleanSetting("Партиклы тотема", true),
           new BooleanSetting("Тайтлы", true),
           new BooleanSetting("Таблица", true),
           new BooleanSetting("Тряску камеры", true),
           new BooleanSetting("Плохие эффекты", true),
           new BooleanSetting("Блеск зачарования", false),
           new BooleanSetting("Сердца визера", false),
           new BooleanSetting("Плохие сердца", false),
           new BooleanSetting("Дождь", true),
           new BooleanSetting("Облака", false),
           new BooleanSetting("Блок-сущности", false),
           new BooleanSetting("Тени", false),
           new BooleanSetting("Частицы", false),
           new BooleanSetting("Частицы удара", false)
   );

   public NoRender() {
      this.addSettings(elements);
   }

   /** Общий помощник: активен ли модуль и включён ли элемент. */
   public static boolean isElementActive(String element) {
      try {
         NoRender inst = vesence.Vesence.get.manager.get(NoRender.class);
         return inst != null && inst.enable && elements.get(element);
      } catch (Throwable ignored) {
         return false;
      }
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player != null) {
         if (elements.get("Плохие эффекты")) {
            if (mc.player.hasStatusEffect(StatusEffects.DARKNESS)) mc.player.removeStatusEffect(StatusEffects.DARKNESS);
            if (mc.player.hasStatusEffect(StatusEffects.BLINDNESS)) mc.player.removeStatusEffect(StatusEffects.BLINDNESS);
            if (mc.player.hasStatusEffect(StatusEffects.NAUSEA)) mc.player.removeStatusEffect(StatusEffects.NAUSEA);
         }
      }
   }
}
