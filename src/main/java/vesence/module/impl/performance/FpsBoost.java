package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.GameOptions;
import net.minecraft.particle.ParticlesMode;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "FPS Boost", description = "Отключает дорогие эффекты рендера для прироста FPS", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class FpsBoost extends PerformanceModule {

   private static FpsBoost instance;

   private final BooleanSetting noEntityShadows = new BooleanSetting("Тени сущностей", true);
   private final BooleanSetting noParticles = new BooleanSetting("Частицы", true);
   private final BooleanSetting noWeather = new BooleanSetting("Погода", true);
   private final BooleanSetting noSky = new BooleanSetting("Небо", false);
   private final BooleanSetting noFog = new BooleanSetting("Туман", true);
   private final BooleanSetting noVignette = new BooleanSetting("Виньетка", true);
   private final BooleanSetting noTotemAnim = new BooleanSetting("Анимация тотема", true);
   private final BooleanSetting noArmorStands = new BooleanSetting("Стенды с бронёй", false);
   private final BooleanSetting simpleBlockModels = new BooleanSetting("Упрощённые блок-модели", false);
   private final BooleanSetting limitFps = new BooleanSetting("Ограничить FPS", false);
   private final SliderSetting fpsLimit = new SliderSetting("Лимит FPS", 120, 30, 260, 5);

   public static final AtomicBoolean SKIP_SKY = new AtomicBoolean(false);
   public static final AtomicBoolean SKIP_FOG = new AtomicBoolean(false);
   public static final AtomicBoolean SKIP_VIGNETTE = new AtomicBoolean(false);
   public static final AtomicBoolean SKIP_TOTEM_ANIM = new AtomicBoolean(false);
   public static final AtomicBoolean SKIP_ARMOR_STANDS = new AtomicBoolean(false);
   public static final AtomicBoolean SIMPLE_BLOCK_MODELS = new AtomicBoolean(false);

   private Boolean origEntityShadows;
   private ParticlesMode origParticles;
   private Integer origWeatherRadius;
   private Boolean origVignette;
   private Integer origMaxFps;

   public FpsBoost() {
      this.addSettings(noEntityShadows, noParticles, noWeather, noSky, noFog, noVignette,
            noTotemAnim, noArmorStands, simpleBlockModels, limitFps, fpsLimit);
      instance = this;
   }

   public static FpsBoost getInstance() {
      return instance;
   }

   @Override
   protected void applyOptimizations() {
      GameOptions o = mc.options;
      if (o == null) {
         return;
      }

      origEntityShadows = o.getEntityShadows().getValue();
      origParticles = o.getParticles().getValue();
      origWeatherRadius = o.getWeatherRadius().getValue();
      origVignette = o.getVignette().getValue();
      origMaxFps = o.getMaxFps().getValue();

      applyOptionState();
      applyFlags();
   }

   private void applyOptionState() {
      GameOptions o = mc.options;
      if (o == null) {
         return;
      }
      if (noEntityShadows.get()) {
         o.getEntityShadows().setValue(false);
      } else if (origEntityShadows != null) {
         o.getEntityShadows().setValue(origEntityShadows);
      }

      if (noParticles.get()) {
         o.getParticles().setValue(ParticlesMode.MINIMAL);
      } else if (origParticles != null) {
         o.getParticles().setValue(origParticles);
      }

      if (noWeather.get()) {
         o.getWeatherRadius().setValue(0);
      } else if (origWeatherRadius != null) {
         o.getWeatherRadius().setValue(origWeatherRadius);
      }

      if (noVignette.get()) {
         o.getVignette().setValue(false);
      } else if (origVignette != null) {
         o.getVignette().setValue(origVignette);
      }

      if (limitFps.get()) {
         o.getMaxFps().setValue((int) fpsLimit.current);
      } else if (origMaxFps != null) {
         o.getMaxFps().setValue(origMaxFps);
      }
   }

   private void applyFlags() {
      SKIP_SKY.set(noSky.get());
      SKIP_FOG.set(noFog.get());
      SKIP_VIGNETTE.set(noVignette.get());
      SKIP_TOTEM_ANIM.set(noTotemAnim.get());
      SKIP_ARMOR_STANDS.set(noArmorStands.get());
      SIMPLE_BLOCK_MODELS.set(simpleBlockModels.get());
   }

   @Override
   protected void restoreOriginals() {
      GameOptions o = mc.options;
      if (o != null) {
         if (origEntityShadows != null) {
            o.getEntityShadows().setValue(origEntityShadows);
         }
         if (origParticles != null) {
            o.getParticles().setValue(origParticles);
         }
         if (origWeatherRadius != null) {
            o.getWeatherRadius().setValue(origWeatherRadius);
         }
         if (origVignette != null) {
            o.getVignette().setValue(origVignette);
         }
         if (origMaxFps != null) {
            o.getMaxFps().setValue(origMaxFps);
         }
      }

      SKIP_SKY.set(false);
      SKIP_FOG.set(false);
      SKIP_VIGNETTE.set(false);
      SKIP_TOTEM_ANIM.set(false);
      SKIP_ARMOR_STANDS.set(false);
      SIMPLE_BLOCK_MODELS.set(false);

      origEntityShadows = null;
      origParticles = null;
      origWeatherRadius = null;
      origVignette = null;
      origMaxFps = null;
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive() || mc.options == null) {
         return;
      }
      applyOptionState();
      applyFlags();
      PerfManager.getInstance().sampleFps();
   }
}
