package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Thread Optimizer", description = "Бюджет параллельной загрузки чанков и приоритеты", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class ThreadOptimizer extends PerformanceModule {

   private static ThreadOptimizer instance;

   private final BooleanSetting prioritizeNearChunks = new BooleanSetting("Приоритет ближних чанков", true);
   private final BooleanSetting autoThreads = new BooleanSetting("Авто число потоков", true);
   private final SliderSetting maxParallelLoads = new SliderSetting("Макс. параллельных загрузок", 4, 1, 16, 1);

   public static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
   public static final AtomicBoolean PRIORITIZE_NEAR = new AtomicBoolean(false);
   public static final AtomicInteger MAX_PARALLEL_CHUNK_LOADS = new AtomicInteger(Integer.MAX_VALUE);

   private final AtomicLong inFlight = new AtomicLong(0);

   public ThreadOptimizer() {
      this.addSettings(prioritizeNearChunks, autoThreads, maxParallelLoads);
      instance = this;
   }

   public static ThreadOptimizer getInstance() {
      return instance;
   }

   @Override
   protected void applyOptimizations() {
      ACTIVE.set(true);
      publish();
   }

   @Override
   protected void restoreOriginals() {
      ACTIVE.set(false);
      PRIORITIZE_NEAR.set(false);
      MAX_PARALLEL_CHUNK_LOADS.set(Integer.MAX_VALUE);
      inFlight.set(0);
   }

   private void publish() {
      PRIORITIZE_NEAR.set(prioritizeNearChunks.get());
      int budget;
      if (autoThreads.get()) {
         int cores = Math.max(1, Runtime.getRuntime().availableProcessors());

         budget = Math.max(1, (int) Math.round((cores - 2) * intensity().scaleDouble(1.0, 0.6)));
      } else {
         budget = (int) maxParallelLoads.current;
      }
      MAX_PARALLEL_CHUNK_LOADS.set(Math.max(1, budget));
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      publish();
      PerfManager.getInstance().reportStatus(this.name,
            "ON / параллельно=" + MAX_PARALLEL_CHUNK_LOADS.get());
   }

   public static boolean tryAcquireLoadSlot() {
      ThreadOptimizer m = instance;
      if (m == null || !m.isActive()) {
         return true;
      }
      long current = m.inFlight.get();
      if (current >= MAX_PARALLEL_CHUNK_LOADS.get()) {
         return false;
      }
      m.inFlight.incrementAndGet();
      return true;
   }

   public static void releaseLoadSlot() {
      ThreadOptimizer m = instance;
      if (m != null) {
         m.inFlight.updateAndGet(v -> Math.max(0, v - 1));
      }
   }
}
