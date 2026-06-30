package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SmoothingFilter {
   private float emaValue;
   private float runningMean;
   private float runningM2;
   private int sampleCount;
   private final float alpha;
   private final float clipSigma;

   public SmoothingFilter(float alpha) {
      this(alpha, 2.0f);
   }

   public SmoothingFilter(float alpha, float clipSigma) {
      this.alpha = alpha;
      this.clipSigma = clipSigma;
   }

   public float filter(float rawValue) {
      emaValue = alpha * rawValue + (1.0f - alpha) * emaValue;

      sampleCount++;
      float delta = emaValue - runningMean;
      runningMean += delta / sampleCount;
      float delta2 = emaValue - runningMean;
      runningM2 += delta * delta2;

      if (sampleCount > 10) {
         float variance = runningM2 / sampleCount;
         if (variance > 0.0001f) {
            float std = (float) Math.sqrt(variance);
            float low = runningMean - clipSigma * std;
            float high = runningMean + clipSigma * std;
            if (emaValue < low) emaValue = low;
            if (emaValue > high) emaValue = high;
         }
      }

      return emaValue;
   }

   public float get() {
      return emaValue;
   }

   public float getMean() {
      return sampleCount > 0 ? runningMean : emaValue;
   }

   public float getStd() {
      if (sampleCount < 2) return 0.5f;
      float variance = runningM2 / (sampleCount - 1);
      return variance > 0 ? (float) Math.sqrt(variance) : 0.5f;
   }

   public void reset() {
      emaValue = 0f;
      runningMean = 0f;
      runningM2 = 0f;
      sampleCount = 0;
   }

   public void reset(float initialValue) {
      emaValue = initialValue;
      runningMean = initialValue;
      runningM2 = 0f;
      sampleCount = 1;
   }
}
