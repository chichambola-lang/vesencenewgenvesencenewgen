package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "World Optimizer", description = "Батч-лайтинг, отложенные блок-сущности, лимиты тиков", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class WorldOptimizer extends PerformanceModule {

   private static WorldOptimizer instance;

   private final BooleanSetting batchLighting = new BooleanSetting("Батч обновлений света", true);
   private final SliderSetting lightBudget = new SliderSetting("Бюджет света/кадр", 512, 64, 4096, 64);
   private final BooleanSetting deferBlockEntities = new BooleanSetting("Отложенные блок-сущности", true);
   private final SliderSetting blockEntityDist = new SliderSetting("Дистанция блок-сущностей (м)", 48, 16, 128, 1);
   private final SliderSetting redstoneDist = new SliderSetting("Дистанция редстоуна (м)", 64, 16, 160, 1);
   private final SliderSetting randomTickDist = new SliderSetting("Дистанция рандом-тиков (м)", 64, 16, 160, 1);

   public static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
   public static final AtomicBoolean BATCH_LIGHTING = new AtomicBoolean(false);
   public static final AtomicInteger LIGHT_BUDGET = new AtomicInteger(Integer.MAX_VALUE);
   public static final AtomicBoolean DEFER_BLOCK_ENTITIES = new AtomicBoolean(false);
   private static volatile double blockEntityDistSq = Double.MAX_VALUE;
   private static volatile double redstoneDistSq = Double.MAX_VALUE;
   private static volatile double randomTickDistSq = Double.MAX_VALUE;

   public WorldOptimizer() {
      this.addSettings(batchLighting, lightBudget, deferBlockEntities, blockEntityDist, redstoneDist, randomTickDist);
      instance = this;
   }

   public static WorldOptimizer getInstance() {
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
      BATCH_LIGHTING.set(false);
      LIGHT_BUDGET.set(Integer.MAX_VALUE);
      DEFER_BLOCK_ENTITIES.set(false);
      blockEntityDistSq = Double.MAX_VALUE;
      redstoneDistSq = Double.MAX_VALUE;
      randomTickDistSq = Double.MAX_VALUE;
   }

   private void publish() {
      Intensity i = intensity();
      BATCH_LIGHTING.set(batchLighting.get());
      if (batchLighting.get()) {
         int budget = (int) Math.round(lightBudget.current * i.scaleDouble(1.0, 0.5));
         LIGHT_BUDGET.set(Math.max(32, budget));
      } else {
         LIGHT_BUDGET.set(Integer.MAX_VALUE);
      }
      DEFER_BLOCK_ENTITIES.set(deferBlockEntities.get());

      double scale = i.scaleDouble(1.0, 0.6);
      double be = blockEntityDist.current * scale;
      double rs = redstoneDist.current * scale;
      double rt = randomTickDist.current * scale;
      blockEntityDistSq = deferBlockEntities.get() ? be * be : Double.MAX_VALUE;
      redstoneDistSq = rs * rs;
      randomTickDistSq = rt * rt;
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      publish();
      PerfManager.getInstance().reportStatus(this.name,
            "ON / свет=" + (batchLighting.get() ? LIGHT_BUDGET.get() : "∞"));
   }

   public static boolean shouldDeferBlockEntity(double distSq) {
      return ACTIVE.get() && DEFER_BLOCK_ENTITIES.get() && distSq > blockEntityDistSq;
   }

   public static boolean isRedstoneTooFar(double distSq) {
      return ACTIVE.get() && distSq > redstoneDistSq;
   }

   public static boolean isRandomTickTooFar(double distSq) {
      return ACTIVE.get() && distSq > randomTickDistSq;
   }

   public static int lightBudgetPerFrame() {
      return ACTIVE.get() && BATCH_LIGHTING.get() ? LIGHT_BUDGET.get() : Integer.MAX_VALUE;
   }
}
