package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ProfileDefaults {
   public static final float AGGRESSIVE_SPEED = 56.0f;
   public static final float NORMAL_SPEED = 47.5f;
   public static final float CALM_SPEED = 25.0f;

   public static final float AGGRESSIVE_JITTER = 0.55f;
   public static final float NORMAL_JITTER = 1.3f;
   public static final float CALM_JITTER = 2.25f;

   public static final float AGGRESSIVE_OVERSHOOT = 0.08f;
   public static final float NORMAL_OVERSHOOT = 0.18f;
   public static final float CALM_OVERSHOOT = 0.30f;

   public static final float AGGRESSIVE_OU_THETA = 0.92f;
   public static final float NORMAL_OU_THETA = 0.85f;
   public static final float CALM_OU_THETA = 0.78f;

   public static final float AGGRESSIVE_OU_SIGMA = 0.15f;
   public static final float NORMAL_OU_SIGMA = 0.30f;
   public static final float CALM_OU_SIGMA = 0.50f;

   public static final float AGGRESSIVE_PERLIN = 0.15f;
   public static final float NORMAL_PERLIN = 0.25f;
   public static final float CALM_PERLIN = 0.35f;

   private ProfileDefaults() {
   }
}
