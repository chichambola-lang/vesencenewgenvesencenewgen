package vesence.module.impl.performance;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(name = "Occlusion Culling", description = "Не рендерит то, что вне поля зрения (за спиной)", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class OcclusionCulling extends PerformanceModule {

   private static OcclusionCulling instance;

   private final BooleanSetting cullEntities = new BooleanSetting("Сущности", true);
   private final SliderSetting keepRadius = new SliderSetting("Радиус сохранения (м)", 6, 1, 24, 1);
   private final SliderSetting fovPadding = new SliderSetting("Запас угла (°)", 25, 0, 60, 1);

   public static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
   public static final AtomicBoolean CULL_ENTITIES = new AtomicBoolean(false);

   private static volatile double dirX, dirY, dirZ;
   private static volatile double cosThreshold = -1.0;
   private static volatile double keepRadiusSq = 36.0;

   public OcclusionCulling() {
      this.addSettings(cullEntities, keepRadius, fovPadding);
      instance = this;
   }

   public static OcclusionCulling getInstance() {
      return instance;
   }

   @Override
   protected void applyOptimizations() {
      ACTIVE.set(true);
      CULL_ENTITIES.set(cullEntities.get());
      recompute();
   }

   @Override
   protected void restoreOriginals() {
      ACTIVE.set(false);
      CULL_ENTITIES.set(false);
   }

   @EventInit
   public void onTick(ClientTickEvent e) {
      if (!isActive()) {
         return;
      }
      CULL_ENTITIES.set(cullEntities.get());
      recompute();
   }

   private void recompute() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.gameRenderer == null || mc.getCameraEntity() == null) {
         return;
      }

      float yaw = mc.getCameraEntity().getYaw();
      float pitch = mc.getCameraEntity().getPitch();
      double radYaw = Math.toRadians(yaw);
      double radPitch = Math.toRadians(pitch);
      double cosPitch = Math.cos(radPitch);
      dirX = -Math.sin(radYaw) * cosPitch;
      dirY = -Math.sin(radPitch);
      dirZ = Math.cos(radYaw) * cosPitch;

      double fov = 70.0;
      try {
         fov = mc.options.getFov().getValue();
      } catch (Exception ignored) {
      }

      double aspect = mc.getWindow() != null && mc.getWindow().getFramebufferHeight() > 0
            ? (double) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight()
            : 1.7778;
      double horizontalFov = Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fov / 2.0)) * aspect));
      double half = horizontalFov / 2.0 + fovPadding.current;

      half -= intensity().scaleDouble(0.0, 10.0);
      half = Math.max(5.0, Math.min(179.0, half));
      cosThreshold = Math.cos(Math.toRadians(half));

      double r = keepRadius.current;
      keepRadiusSq = r * r;

      PerfManager.getInstance().reportStatus(this.name, "ON / FOV±" + (int) half + "°");
   }

   public static boolean isOutsideView(Entity entity, double ex, double ey, double ez,
                                       double camX, double camY, double camZ) {
      if (!ACTIVE.get() || !CULL_ENTITIES.get()) {
         return false;
      }
      double vx = ex - camX;
      double vy = ey - camY;
      double vz = ez - camZ;
      double distSq = vx * vx + vy * vy + vz * vz;

      if (distSq <= keepRadiusSq) {
         return false;
      }
      double len = Math.sqrt(distSq);
      if (len < 1.0e-4) {
         return false;
      }
      double dot = (vx * dirX + vy * dirY + vz * dirZ) / len;

      return dot < cosThreshold;
   }
}
