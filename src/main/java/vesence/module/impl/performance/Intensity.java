package vesence.module.impl.performance;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum Intensity {
   LOW(0.25f),
   MEDIUM(0.5f),
   HIGH(0.75f),
   EXTREME(1.0f);

   public static final String[] NAMES = {"LOW", "MEDIUM", "HIGH", "EXTREME"};

   private final float factor;

   Intensity(float factor) {
      this.factor = factor;
   }

   public float factor() {
      return factor;
   }

   public float scale(float low, float high) {
      return low + (high - low) * factor;
   }

   public int scaleInt(int low, int high) {
      return Math.round(scale(low, high));
   }

   public double scaleDouble(double low, double high) {
      return low + (high - low) * factor;
   }

   public static Intensity from(String name) {
      if (name == null) {
         return MEDIUM;
      }
      try {
         return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException e) {
         return MEDIUM;
      }
   }
}
