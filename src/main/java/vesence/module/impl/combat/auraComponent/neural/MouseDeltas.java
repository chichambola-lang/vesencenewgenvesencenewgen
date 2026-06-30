package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class MouseDeltas {
   public static volatile double lastRawYaw = 0.0;
   public static volatile double lastRawPitch = 0.0;
   public static volatile boolean lastEventCancelled = false;
   public static volatile boolean lastChangeLookCalled = false;

   private MouseDeltas() {
   }

   public static void set(double yaw, double pitch) {
      lastRawYaw = yaw;
      lastRawPitch = pitch;
   }

   public static float rawYawFloat() {
      return (float) lastRawYaw;
   }

   public static float rawPitchFloat() {
      return (float) lastRawPitch;
   }
}
