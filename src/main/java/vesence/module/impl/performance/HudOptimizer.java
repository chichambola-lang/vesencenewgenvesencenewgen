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

@IModule(name = "HUD Optimizer", description = "Кэш текста, реже перерисовка оверлеев, лимит чата", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class HudOptimizer extends PerformanceModule {

   private static HudOptimizer instance;

   private final BooleanSetting cacheText = new BooleanSetting("Кэш текста", true);
   private final BooleanSetting throttleOverlays = new BooleanSetting("Реже перерисовка оверлеев", true);
   private final SliderSetting overlayInterval = new SliderSetting("Интервал перерисовки (кадры)", 2, 1, 8, 1);
   private final BooleanSetting skipTransparent = new BooleanSetting("Пропуск прозрачных", true);
   private final BooleanSetting limitChat = new BooleanSetting("Лимит сообщений чата", true);
   private final SliderSetting chatLimit = new SliderSetting("Макс. сообщений чата", 100, 20, 500, 10);

   public static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
   public static final AtomicBoolean CACHE_TEXT = new AtomicBoolean(false);
   public static final AtomicBoolean SKIP_TRANSPARENT = new AtomicBoolean(false);
   public static final AtomicInteger OVERLAY_INTERVAL = new AtomicInteger(1);
   public static final AtomicInteger CHAT_LIMIT = new AtomicInteger(Integer.MAX_VALUE);

   private final AtomicLong frameId = new AtomicLong(0);

   public HudOptimizer() {
      this.addSettings(cacheText, throttleOverlays, overlayInterval, skipTransparent, limitChat, chatLimit);
      instance = this;
   }

   public static HudOptimizer getInstance() {
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
      CACHE_TEXT.set(false);
      SKIP_TRANSPARENT.set(false);
      OVERLAY_INTERVAL.set(1);
      CHAT_LIMIT.set(Integer.MAX_VALUE);
   }

   private void publish() {
      CACHE_TEXT.set(cacheText.get());
      SKIP_TRANSPARENT.set(skipTransparent.get());
      if (throttleOverlays.get()) {
         int base = (int) overlayInterval.current;

         int extra = intensity().scaleInt(0, 2);
         OVERLAY_INTERVAL.set(Math.max(1, base + extra));
      } else {
         OVERLAY_INTERVAL.set(1);
      }
      CHAT_LIMIT.set(limitChat.get() ? (int) chatLimit.current : Integer.MAX_VALUE);
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      publish();
      frameId.incrementAndGet();
      PerfManager.getInstance().reportStatus(this.name,
            "ON / оверлеи 1/" + OVERLAY_INTERVAL.get() + " чат≤" + CHAT_LIMIT.get());
   }

   public static boolean shouldRedrawOverlay(long currentFrame) {
      if (!ACTIVE.get()) {
         return true;
      }
      int interval = OVERLAY_INTERVAL.get();
      return interval <= 1 || currentFrame % interval == 0;
   }

   public static boolean isSkippableColor(int argb) {
      return ACTIVE.get() && SKIP_TRANSPARENT.get() && ((argb >>> 24) == 0);
   }

   public static int chatLimit() {
      return ACTIVE.get() ? CHAT_LIMIT.get() : Integer.MAX_VALUE;
   }
}
