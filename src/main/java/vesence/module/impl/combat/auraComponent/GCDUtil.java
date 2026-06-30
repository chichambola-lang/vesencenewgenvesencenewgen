package vesence.module.impl.combat.auraComponent;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public class GCDUtil implements IMinecraft {
   public static float getSensitivity(float rot) {
      return getDeltaMouse(rot) * getGCDValue();
   }

   public static float getGCDValue() {
      return (float)(getGCD() * 0.15);
   }

   public static float getGCD() {
      double f = 0.5 * 0.6000000238418579D + 0.20000000298023224D;
      return (float)(f * f * f * 8.0D);
   }

   public static float getDeltaMouse(float delta) {
      return Math.round(delta / getGCDValue());
   }
}
