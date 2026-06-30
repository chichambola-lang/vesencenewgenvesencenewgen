package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public final class ProfileManager {
   private static float currentAggression = 0.5f;
   private static float currentSpeed = ProfileDefaults.NORMAL_SPEED;
   private static float currentJitter = ProfileDefaults.NORMAL_JITTER;
   private static float currentOvershoot = ProfileDefaults.NORMAL_OVERSHOOT;
   private static float currentOuTheta = ProfileDefaults.NORMAL_OU_THETA;
   private static float currentOuSigma = ProfileDefaults.NORMAL_OU_SIGMA;
   private static float currentPerlinFreq = ProfileDefaults.NORMAL_PERLIN;

   public static void tick() {
   }

   public static void reset() {
      currentAggression = 0.5f;
      currentSpeed = ProfileDefaults.NORMAL_SPEED;
      currentJitter = ProfileDefaults.NORMAL_JITTER;
      currentOvershoot = ProfileDefaults.NORMAL_OVERSHOOT;
      currentOuTheta = ProfileDefaults.NORMAL_OU_THETA;
      currentOuSigma = ProfileDefaults.NORMAL_OU_SIGMA;
      currentPerlinFreq = ProfileDefaults.NORMAL_PERLIN;
   }

   public static void setAggression(float v) { currentAggression = MathHelper.clamp(v, 0.0f, 1.0f); }
   public static float getAggression() { return currentAggression; }

   public static void setSpeed(float v) { currentSpeed = v; }
   public static float getSpeed() { return currentSpeed; }

   public static void setJitter(float v) { currentJitter = v; }
   public static float getJitter() { return currentJitter; }

   public static void setOvershoot(float v) { currentOvershoot = MathHelper.clamp(v, 0.0f, 1.0f); }
   public static float getOvershoot() { return currentOvershoot; }

   public static void setOuTheta(float v) { currentOuTheta = MathHelper.clamp(v, 0.0f, 1.0f); }
   public static float getOuTheta() { return currentOuTheta; }

   public static void setOuSigma(float v) { currentOuSigma = MathHelper.clamp(v, 0.0f, 1.0f); }
   public static float getOuSigma() { return currentOuSigma; }

   public static void setPerlinFreq(float v) { currentPerlinFreq = v; }
   public static float getPerlinFreq() { return currentPerlinFreq; }

   private ProfileManager() {
   }
}
