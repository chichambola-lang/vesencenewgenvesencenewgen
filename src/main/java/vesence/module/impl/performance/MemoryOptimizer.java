package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Memory Optimizer", description = "Управление памятью: GC, кэши, интернинг строк", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class MemoryOptimizer extends PerformanceModule {

   private static MemoryOptimizer instance;

   private final BooleanSetting aggressiveGc = new BooleanSetting("Агрессивный GC", false);
   private final SliderSetting gcThreshold = new SliderSetting("Порог GC (% heap)", 80, 50, 95, 1, true);
   private final SliderSetting gcInterval = new SliderSetting("Интервал GC (сек)", 30, 5, 120, 1);
   private final BooleanSetting limitChunkRenderers = new BooleanSetting("Лимит чанк-рендереров", true);
   private final SliderSetting maxCompiledChunks = new SliderSetting("Макс. скомпилир. чанков", 512, 128, 2048, 32);
   private final BooleanSetting internStrings = new BooleanSetting("Интернинг строк", true);
   private final SliderSetting pathfindingCacheLimit = new SliderSetting("Лимит кэша путей", 256, 64, 1024, 32);

   public static final AtomicInteger MAX_COMPILED_CHUNKS = new AtomicInteger(Integer.MAX_VALUE);
   public static final AtomicInteger PATHFINDING_CACHE_LIMIT = new AtomicInteger(Integer.MAX_VALUE);

   private final AtomicLong lastGc = new AtomicLong(0L);
   private final AtomicLong lastGcCount = new AtomicLong(0L);

   public MemoryOptimizer() {
      this.addSettings(aggressiveGc, gcThreshold, gcInterval, limitChunkRenderers,
            maxCompiledChunks, internStrings, pathfindingCacheLimit);
      instance = this;
   }

   public static MemoryOptimizer getInstance() {
      return instance;
   }

   public static boolean isInterningEnabled() {
      MemoryOptimizer m = instance;
      return m != null && m.isActive() && m.internStrings.get();
   }

   @Override
   protected void applyOptimizations() {
      publish();
      lastGc.set(System.currentTimeMillis());
   }

   @Override
   protected void restoreOriginals() {
      MAX_COMPILED_CHUNKS.set(Integer.MAX_VALUE);
      PATHFINDING_CACHE_LIMIT.set(Integer.MAX_VALUE);
   }

   private void publish() {
      Intensity i = intensity();
      if (limitChunkRenderers.get()) {

         int budget = (int) Math.round(maxCompiledChunks.current * i.scaleDouble(1.0, 0.6));
         MAX_COMPILED_CHUNKS.set(Math.max(64, budget));
      } else {
         MAX_COMPILED_CHUNKS.set(Integer.MAX_VALUE);
      }
      PATHFINDING_CACHE_LIMIT.set((int) pathfindingCacheLimit.current);
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      publish();
      maybeGc();
      PerfManager.getInstance().reportStatus(this.name,
            "ON / heap " + heapUsagePercent() + "% / GC×" + lastGcCount.get());
   }

   private void maybeGc() {
      long now = System.currentTimeMillis();
      long interval = (long) (gcInterval.current * 1000L);
      if (aggressiveGc.get()) {
         interval = Math.max(3000L, interval / 2);
      }
      if (now - lastGc.get() < interval) {
         return;
      }

      int usage = heapUsagePercent();
      double threshold = gcThreshold.current;
      if (aggressiveGc.get()) {
         threshold = Math.max(40.0, threshold - intensity().scaleDouble(5.0, 20.0));
      }
      if (usage >= threshold) {
         System.gc();
         lastGc.set(now);
         lastGcCount.incrementAndGet();
         PerfManager.LOGGER.info("[Memory Optimizer] GC hint at {}% heap usage", usage);
      } else {

         lastGc.set(now - interval + 2000L);
      }
   }

   private static int heapUsagePercent() {
      Runtime rt = Runtime.getRuntime();
      long max = rt.maxMemory();
      if (max <= 0) {
         return 0;
      }
      long used = rt.totalMemory() - rt.freeMemory();
      return (int) (used * 100L / max);
   }

   public static String intern(String s) {
      if (s != null && isInterningEnabled() && s.length() <= 64) {
         return s.intern();
      }
      return s;
   }
}
