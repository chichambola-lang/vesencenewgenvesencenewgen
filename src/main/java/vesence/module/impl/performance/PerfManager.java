package vesence.module.impl.performance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class PerfManager {
   public static final Logger LOGGER = LoggerFactory.getLogger("Vesence/Perf");

   private static final PerfManager INSTANCE = new PerfManager();

   private static final int FPS_WINDOW = 120;

   private final int[] fpsSamples = new int[FPS_WINDOW];
   private final AtomicInteger fpsIndex = new AtomicInteger(0);
   private final AtomicInteger fpsCount = new AtomicInteger(0);

   private final AtomicInteger baselineFps = new AtomicInteger(0);
   private final AtomicLong lastSampleTime = new AtomicLong(0L);

   private final Map<String, String> moduleStatus = new ConcurrentHashMap<>();

   private PerfManager() {
   }

   public static PerfManager getInstance() {
      return INSTANCE;
   }

   public void sampleFps() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null) {
         return;
      }
      int fps = mc.getCurrentFps();
      if (fps <= 0) {
         return;
      }
      long now = System.currentTimeMillis();

      if (now - lastSampleTime.get() < 100L) {
         return;
      }
      lastSampleTime.set(now);

      int idx = fpsIndex.getAndUpdate(i -> (i + 1) % FPS_WINDOW);
      fpsSamples[idx] = fps;
      fpsCount.updateAndGet(c -> Math.min(c + 1, FPS_WINDOW));
   }

   public int getCurrentFps() {
      MinecraftClient mc = MinecraftClient.getInstance();
      return mc == null ? 0 : mc.getCurrentFps();
   }

   public int getAverageFps() {
      int count = fpsCount.get();
      if (count == 0) {
         return getCurrentFps();
      }
      long sum = 0;
      for (int i = 0; i < count; i++) {
         sum += fpsSamples[i];
      }
      return (int) (sum / count);
   }

   public void captureBaseline() {
      baselineFps.compareAndSet(0, getCurrentFps());
   }

   public void resetBaseline() {
      baselineFps.set(0);
   }

   public int getBaselineFps() {
      return baselineFps.get();
   }

   public int getFpsImpact() {
      int base = baselineFps.get();
      if (base <= 0) {
         return 0;
      }
      return getAverageFps() - base;
   }

   public void reportStatus(String moduleName, String status) {
      if (moduleName != null && status != null) {
         moduleStatus.put(moduleName, status);
      }
   }

   public void clearStatus(String moduleName) {
      if (moduleName != null) {
         moduleStatus.remove(moduleName);
      }
   }

   public Map<String, String> getModuleStatus() {
      return moduleStatus;
   }
}
