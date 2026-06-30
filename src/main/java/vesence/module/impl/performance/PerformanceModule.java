package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.ModeSetting;

@Environment(EnvType.CLIENT)
public abstract class PerformanceModule extends Module {

   protected final AtomicBoolean active = new AtomicBoolean(false);

   protected final ModeSetting intensitySetting =
         new ModeSetting("Интенсивность", "MEDIUM", Intensity.NAMES);

   protected PerformanceModule() {

      this.addSettings(intensitySetting);

      this.hiddenFromGui = true;
   }

   public final Intensity intensity() {
      return Intensity.from(intensitySetting.get());
   }

   public final ModeSetting intensitySetting() {
      return intensitySetting;
   }

   public final boolean isActive() {
      return active.get();
   }

   @Override
   public void onEnable() {
      super.onEnable();
      active.set(true);
      PerfManager.getInstance().captureBaseline();
      try {
         applyOptimizations();
         PerfManager.LOGGER.info("[{}] enabled (intensity={})", this.name, intensity());
      } catch (Throwable t) {
         PerfManager.LOGGER.error("[{}] failed to apply optimizations: {}", this.name, t.toString());
      }
      PerfManager.getInstance().reportStatus(this.name, "ON / " + intensity());
   }

   @Override
   public void onDisable() {
      active.set(false);
      try {
         restoreOriginals();
      } catch (Throwable t) {
         PerfManager.LOGGER.error("[{}] failed to restore originals: {}", this.name, t.toString());
      } finally {
         releaseResources();
      }
      PerfManager.getInstance().clearStatus(this.name);
      super.onDisable();
   }

   protected abstract void applyOptimizations();

   protected abstract void restoreOriginals();

   protected void releaseResources() {
   }
}
