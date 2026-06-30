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
           new BooleanSetting("Линия босса", true),
           new BooleanSetting("Анимация тотема", true),
           new BooleanSetting("Партиклы тотема", true),
           new BooleanSetting("Тайтлы", true),
           new BooleanSetting("Таблица", true),
           new BooleanSetting("Тряску камеры", true),
           new BooleanSetting("Плохие эффекты", true),
           new BooleanSetting("Дождь", true)
   );

   public NoRender() {
      this.addSettings(elements);
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
