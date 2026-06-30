package vesence.module.impl.performance;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
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

@IModule(name = "Crash Reporter", description = "Ловит краши с контекстом и пытается восстановиться", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class CrashReporter extends PerformanceModule {

   private static final int FRAME_WINDOW = 120;
   private static CrashReporter instance;

   private final BooleanSetting attemptRecovery = new BooleanSetting("Пытаться восстановиться", true);
   private final BooleanSetting writeReport = new BooleanSetting("Сохранять отчёт", true);

   private final long[] frameTimes = new long[FRAME_WINDOW];
   private final AtomicInteger frameIndex = new AtomicInteger(0);
   private final AtomicLong lastFrameNanos = new AtomicLong(0L);
   private final AtomicBoolean handlerInstalled = new AtomicBoolean(false);

   private Thread.UncaughtExceptionHandler previousHandler;
   private final AtomicInteger recoveries = new AtomicInteger(0);

   public CrashReporter() {
      this.addSettings(attemptRecovery, writeReport);
      instance = this;
   }

   public static CrashReporter getInstance() {
      return instance;
   }

   @Override
   protected void applyOptimizations() {
      installHandler();
   }

   @Override
   protected void restoreOriginals() {
      uninstallHandler();
   }

   private void installHandler() {
      if (handlerInstalled.compareAndSet(false, true)) {
         Thread renderThread = Thread.currentThread();
         previousHandler = renderThread.getUncaughtExceptionHandler();
         renderThread.setUncaughtExceptionHandler((t, e) -> {
            handleCrash(t, e);

            if (previousHandler != null) {
               previousHandler.uncaughtException(t, e);
            }
         });
      }
   }

   private void uninstallHandler() {
      if (handlerInstalled.compareAndSet(true, false)) {
         try {
            Thread.currentThread().setUncaughtExceptionHandler(previousHandler);
         } catch (Exception ignored) {
         }
         previousHandler = null;
      }
   }

   private void handleCrash(Thread thread, Throwable throwable) {
      try {
         PerfManager.LOGGER.error("[Crash Reporter] Caught throwable on {}: {}", thread.getName(), throwable.toString());
         if (writeReport.get()) {
            writeReport(thread, throwable);
         }
         if (attemptRecovery.get()) {
            attemptRecovery();
         }
      } catch (Throwable t) {

         PerfManager.LOGGER.error("[Crash Reporter] Failed while handling crash: {}", t.toString());
      }
   }

   private void attemptRecovery() {
      try {

         vesence.Vesence.invalidateGlContext();
         recoveries.incrementAndGet();
         PerfManager.LOGGER.warn("[Crash Reporter] Recovery attempt #{}: invalidated Vesence GL caches", recoveries.get());
      } catch (Throwable t) {
         PerfManager.LOGGER.error("[Crash Reporter] Recovery failed: {}", t.toString());
      }
   }

   private void writeReport(Thread thread, Throwable throwable) {
      try {
         File dir = new File("Vesence", "crash-reports");
         if (!dir.exists() && !dir.mkdirs()) {
            PerfManager.LOGGER.error("[Crash Reporter] Could not create report directory");
            return;
         }
         String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
         File file = new File(dir, "vesence-crash-" + stamp + ".txt");
         try (PrintWriter pw = new PrintWriter(new FileWriter(file))) {
            pw.println("=== Vesence Crash Report ===");
            pw.println("Time: " + stamp);
            pw.println("Thread: " + thread.getName());
            pw.println();
            pw.println("-- Throwable --");
            throwable.printStackTrace(pw);
            pw.println();
            writeMemory(pw);
            writeRenderState(pw);
            writeThreads(pw);
            writeFrameTimings(pw);
         }
         PerfManager.LOGGER.error("[Crash Reporter] Report written to {}", file.getAbsolutePath());
      } catch (Throwable t) {
         PerfManager.LOGGER.error("[Crash Reporter] Failed to write report: {}", t.toString());
      }
   }

   private void writeMemory(PrintWriter pw) {
      Runtime rt = Runtime.getRuntime();
      long max = rt.maxMemory();
      long total = rt.totalMemory();
      long free = rt.freeMemory();
      pw.println("-- Memory --");
      pw.println("Max:   " + (max / 1048576L) + " MB");
      pw.println("Total: " + (total / 1048576L) + " MB");
      pw.println("Used:  " + ((total - free) / 1048576L) + " MB");
      pw.println();
   }

   private void writeRenderState(PrintWriter pw) {
      pw.println("-- Render / Pipeline --");
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc != null) {
            pw.println("FPS: " + mc.getCurrentFps());
            if (mc.getWindow() != null) {
               pw.println("Framebuffer: " + mc.getWindow().getFramebufferWidth() + "x" + mc.getWindow().getFramebufferHeight());
            }
            pw.println("World loaded: " + (mc.world != null));
            pw.println("Player present: " + (mc.player != null));
         }
         pw.println("GL_VENDOR:   " + safeGl(org.lwjgl.opengl.GL11.GL_VENDOR));
         pw.println("GL_RENDERER: " + safeGl(org.lwjgl.opengl.GL11.GL_RENDERER));
         pw.println("GL_VERSION:  " + safeGl(org.lwjgl.opengl.GL11.GL_VERSION));
         pw.println("GL error:    " + org.lwjgl.opengl.GL11.glGetError());
      } catch (Throwable t) {
         pw.println("(render state unavailable: " + t + ")");
      }
      pw.println();
   }

   private static String safeGl(int name) {
      try {
         return org.lwjgl.opengl.GL11.glGetString(name);
      } catch (Throwable t) {
         return "<unavailable>";
      }
   }

   private void writeThreads(PrintWriter pw) {
      pw.println("-- Threads (" + Thread.activeCount() + " active) --");
      try {
         for (Thread t : Thread.getAllStackTraces().keySet()) {
            pw.println(t.getName() + " [" + t.getState() + "]" + (t.isDaemon() ? " (daemon)" : ""));
         }
      } catch (Throwable t) {
         pw.println("(thread dump unavailable: " + t + ")");
      }
      pw.println();
   }

   private void writeFrameTimings(PrintWriter pw) {
      pw.println("-- Last frame timings (ms) --");
      int count = Math.min(frameIndex.get(), FRAME_WINDOW);
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < count; i++) {
         sb.append(String.format("%.2f ", frameTimes[i] / 1_000_000.0));
      }
      pw.println(sb.toString().trim());
      pw.println();
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      long now = System.nanoTime();
      long last = lastFrameNanos.getAndSet(now);
      if (last != 0L) {
         int idx = frameIndex.getAndUpdate(i -> (i + 1) % FRAME_WINDOW);
         frameTimes[idx] = now - last;
      }
      PerfManager.getInstance().reportStatus(this.name,
            "ON / восстановлений=" + recoveries.get());
   }
}
