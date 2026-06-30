package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Render Optimizer", description = "LOD сущностей, дистанционный куллинг и троттлинг обновлений", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class RenderOptimizer extends PerformanceModule {

   public enum Lod { FULL, MEDIUM, LOW, CULLED }

   private static RenderOptimizer instance;

   private final BooleanSetting frustumCulling = new BooleanSetting("Frustum culling", true);
   private final BooleanSetting entityLod = new BooleanSetting("LOD сущностей", true);
   private final BooleanSetting throttleUpdates = new BooleanSetting("Троттлинг обновлений", true);
   private final SliderSetting fullDist = new SliderSetting("Полная детализация (м)", 16, 4, 32, 1);
   private final SliderSetting mediumDist = new SliderSetting("Средняя детализация (м)", 48, 16, 80, 1);
   private final SliderSetting lowDist = new SliderSetting("Низкая детализация (м)", 96, 48, 160, 1);

   public static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
   public static final AtomicBoolean LOD_ENABLED = new AtomicBoolean(false);
   public static final AtomicBoolean THROTTLE_ENABLED = new AtomicBoolean(false);
   private static volatile double fullDistSq = 16 * 16;
   private static volatile double mediumDistSq = 48 * 48;
   private static volatile double lowDistSq = 96 * 96;

   private final AtomicInteger tickCounter = new AtomicInteger(0);

   public RenderOptimizer() {
      this.addSettings(frustumCulling, entityLod, throttleUpdates, fullDist, mediumDist, lowDist);
      instance = this;
   }

   public static RenderOptimizer getInstance() {
      return instance;
   }

   @Override
   protected void applyOptimizations() {
      publishThresholds();
      ACTIVE.set(true);
      LOD_ENABLED.set(entityLod.get());
      THROTTLE_ENABLED.set(throttleUpdates.get());
   }

   @Override
   protected void restoreOriginals() {
      ACTIVE.set(false);
      LOD_ENABLED.set(false);
      THROTTLE_ENABLED.set(false);
   }

   private void publishThresholds() {
      Intensity i = intensity();

      double scale = i.scaleDouble(1.0, 0.55);
      double full = fullDist.current * scale;
      double medium = mediumDist.current * scale;
      double low = lowDist.current * scale;
      fullDistSq = full * full;
      mediumDistSq = medium * medium;
      lowDistSq = low * low;
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      publishThresholds();
      LOD_ENABLED.set(entityLod.get());
      THROTTLE_ENABLED.set(throttleUpdates.get());
      tickCounter.incrementAndGet();
      PerfManager.getInstance().reportStatus(this.name,
            "ON / " + intensity() + " / LOD=" + (entityLod.get() ? "вкл" : "выкл"));
   }

   public static boolean shouldCull(double distSq) {
      return ACTIVE.get() && distSq > lowDistSq;
   }

   public static Lod classify(double distSq) {
      if (!ACTIVE.get() || !LOD_ENABLED.get()) {
         return Lod.FULL;
      }
      if (distSq <= fullDistSq) {
         return Lod.FULL;
      }
      if (distSq <= mediumDistSq) {
         return Lod.MEDIUM;
      }
      if (distSq <= lowDistSq) {
         return Lod.LOW;
      }
      return Lod.CULLED;
   }

   public static boolean shouldThrottleUpdate(Entity entity, double distSq, long frameId) {
      if (!ACTIVE.get() || !THROTTLE_ENABLED.get()) {
         return false;
      }
      int interval;
      if (distSq <= mediumDistSq) {
         interval = 1;
      } else if (distSq <= lowDistSq) {
         interval = 2;
      } else {
         interval = 4;
      }
      if (interval <= 1) {
         return false;
      }

      return (frameId + entity.getId()) % interval != 0;
   }
}
