package vesence.utils.player;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import vesence.event.player.EventInput;
import vesence.module.impl.combat.AttackAura;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class MoveUtil implements IMinecraft {

   public static double direction(float rotationYaw, float moveForward, float moveStrafing) {
      if (moveForward < 0.0F) {
         rotationYaw += 180.0F;
      }

      float forward = 1.0F;
      if (moveForward < 0.0F) {
         forward = -0.5F;
      } else if (moveForward > 0.0F) {
         forward = 0.5F;
      }

      if (moveStrafing > 0.0F) {
         rotationYaw -= 90.0F * forward;
      }

      if (moveStrafing < 0.0F) {
         rotationYaw += 90.0F * forward;
      }

      return Math.toRadians(rotationYaw);
   }

   public static boolean isMoving() {
      if (mc.player == null || mc.player.input == null) return false;
      net.minecraft.util.math.Vec2f move = mc.player.input.getMovementInput();
      return move.x != 0.0F || move.y != 0.0F;
   }

   public static double[] calculateDirection(double distance) {
      float[] movement = getMovementFromKeys();
      return calculateDirection(movement[0], movement[1], distance);
   }

   public static double[] calculateDirection(float forward, float sideways, double distance) {
      float yaw = mc.player.getYaw();
      if (forward != 0.0F) {
         if (sideways > 0.0F) {
            yaw += forward > 0.0F ? -45.0F : 45.0F;
         } else if (sideways < 0.0F) {
            yaw += forward > 0.0F ? 45.0F : -45.0F;
         }

         sideways = 0.0F;
         forward = forward > 0.0F ? 1.0F : -1.0F;
      }

      double sinYaw = Math.sin(Math.toRadians(yaw + 90.0F));
      double cosYaw = Math.cos(Math.toRadians(yaw + 90.0F));
      double xMovement = forward * distance * cosYaw + sideways * distance * sinYaw;
      double zMovement = forward * distance * sinYaw - sideways * distance * cosYaw;
      return new double[]{xMovement, zMovement};
   }

   public static void fixMovement(EventInput event, float cameraYaw) {
      float forward = event.getForward();
      float strafe = event.getStrafe();

      if (forward == 0.0F && strafe == 0.0F) {
         return;
      }

      float effectiveYaw = mc.player.isGliding() ? mc.player.getYaw() : cameraYaw;
      double angle = MathHelper.wrapDegrees(Math.toDegrees(direction(effectiveYaw, forward, strafe)));

      float closestForward = 0.0F;
      float closestStrafe = 0.0F;
      float closestDifference = Float.MAX_VALUE;

      for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
         for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
            if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;

            double predictedAngle = MathHelper.wrapDegrees(
                    Math.toDegrees(direction(mc.player.getYaw(), predictedForward, predictedStrafe))
            );

            double difference = Math.abs(angle - predictedAngle);
            if (difference < closestDifference) {
               closestDifference = (float) difference;
               closestForward = predictedForward;
               closestStrafe = predictedStrafe;
            }
         }
      }

      event.setForward(closestForward);
      event.setStrafe(closestStrafe);
   }

   public static void fixMovement(float cameraYaw) {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];

      if (forward == 0.0F && strafe == 0.0F) {
         return;
      }

      float effectiveYaw = mc.player.isGliding() ? mc.player.getYaw() : cameraYaw;
      double angle = MathHelper.wrapDegrees(
              Math.toDegrees(direction(effectiveYaw, forward, strafe))
      );

      float closestForward = 0.0F;
      float closestStrafe = 0.0F;
      float closestDifference = Float.MAX_VALUE;

      for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
         for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
            if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;

            double predictedAngle = MathHelper.wrapDegrees(
                    Math.toDegrees(direction(mc.player.getYaw(), predictedForward, predictedStrafe))
            );

            double difference = Math.abs(angle - predictedAngle);
            if (difference < closestDifference) {
               closestDifference = (float) difference;
               closestForward = predictedForward;
               closestStrafe = predictedStrafe;
            }
         }
      }

      mc.options.forwardKey.setPressed(closestForward > 0.0F);
      mc.options.backKey.setPressed(closestForward < 0.0F);
      mc.options.leftKey.setPressed(closestStrafe > 0.0F);
      mc.options.rightKey.setPressed(closestStrafe < 0.0F);
   }

   public static float[] getMovementFromKeys() {
      float forward = 0.0F;
      float strafe = 0.0F;
      if (mc.options.forwardKey.isPressed()) {
         forward++;
      }
      if (mc.options.backKey.isPressed()) {
         forward--;
      }
      if (mc.options.leftKey.isPressed()) {
         strafe++;
      }
      if (mc.options.rightKey.isPressed()) {
         strafe--;
      }
      return new float[]{forward, strafe};
   }

   public static void targetMovement(float cameraYaw, Vec3d position) {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];
      if (forward != 0.0F || strafe != 0.0F) {
         Box box = AttackAura.target.getBoundingBox();
         double randX = MathHelper.lerp(Math.random(), box.minX, box.maxX);
         double randY = MathHelper.lerp(Math.random(), box.minY, box.maxY);
         double randZ = MathHelper.lerp(Math.random(), box.minZ, box.maxZ);
         randY = MathHelper.clamp(randY, AttackAura.target.getY() + 0.2, AttackAura.target.getY() + AttackAura.target.getHeight() - 0.2);
         Vec3d randomHitVec = new Vec3d(randX, randY, randZ);
         Vec3d direction = randomHitVec.subtract(mc.player.getEyePos()).normalize();
         float targetYaw = (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0);
         double angle = MathHelper.wrapDegrees(
                 Math.toDegrees(direction(mc.player.isGliding() ? mc.player.getYaw() : targetYaw, forward, strafe))
         );
         float closestForward = 0.0F;
         float closestStrafe = 0.0F;
         float closestDifference = Float.MAX_VALUE;

         for (float predictedForward = -1.0F; predictedForward <= 1.0F; predictedForward += 1.0F) {
            for (float predictedStrafe = -1.0F; predictedStrafe <= 1.0F; predictedStrafe += 1.0F) {
               if (predictedStrafe == 0.0F && predictedForward == 0.0F) continue;
               double predictedAngle = MathHelper.wrapDegrees(
                       Math.toDegrees(direction(mc.player.getYaw(), predictedForward, predictedStrafe))
               );
               double difference = Math.abs(angle - predictedAngle);
               if (difference < closestDifference) {
                  closestDifference = (float)difference;
                  closestForward = predictedForward;
                  closestStrafe = predictedStrafe;
               }
            }
         }

         mc.options.forwardKey.setPressed(closestForward > 0.0F);
         mc.options.backKey.setPressed(closestForward < 0.0F);
         mc.options.leftKey.setPressed(closestStrafe > 0.0F);
         mc.options.rightKey.setPressed(closestStrafe < 0.0F);
      }
   }

   public static float getdir() {
      float[] movement = getMovementFromKeys();
      float forward = movement[0];
      float strafe = movement[1];
      if (forward == 0.0F && strafe == 0.0F) return -1.0F;
      return (float) Math.toDegrees(direction(mc.player.getYaw(), forward, strafe));
   }

   @Generated
   private MoveUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
