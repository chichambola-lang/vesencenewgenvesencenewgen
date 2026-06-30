package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.EventMoveInput;
import vesence.event.player.EventOnMovePost;
import vesence.event.player.EventPostMotion;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.utils.TimerManager;
import vesence.utils.network.NetworkUtils;
import vesence.utils.player.MoveUtil;

@IModule(
   name = "Speed",
   description = "Ускоряет движение игрока разными способами",
   category = Category.MOVEMENT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Speed extends Module {
   public static ModeSetting mode = new ModeSetting("Mode", "Ares-Entity", "Ares-Entity", "Grim-Entity", "GrimGlide", "RW TEST");
   private int rwTicks;
   private int rwGroundTicks;

   public Speed() {
      this.addSettings(new Setting[]{mode});
   }

   @EventInit
   public void onUpdate(ClientTickEvent e) {
      if (!this.enable) return;
      if (mc.player == null || mc.world == null) return;

      if (mode.is("Grim-Entity")) {
         double finalSpeed = 6.0E-4F;

         Entity nearest = null;
         double bestSq = Double.MAX_VALUE;
         double maxRangeSq = 0.2F;

         for (Entity ent : mc.world.getEntities()) {
            if (ent != mc.player) {

               double dx = ent.getX() - mc.player.getX();
               double dz = ent.getZ() - mc.player.getZ();
               double sq = dx * dx + dz * dz;
               if (sq <= maxRangeSq && sq < bestSq) {
                  bestSq = sq;
                  nearest = ent;
               }
            }
         }

          if (nearest != null) {
             double[] dir = this.getDirectionToPoint(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()), new Vec3d(nearest.getX(), nearest.getY(), nearest.getZ()), finalSpeed);
             mc.player.addVelocity(dir[0], 0.0, dir[1]);
          }
       }

       if (mode.is("GrimGlide")) {
           if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
              double yaw = Math.toRadians(mc.player.getYaw());
              double xt = -Math.sin(yaw);
              double zt = Math.cos(yaw);
              Vec3d vel = mc.player.getVelocity();
              mc.player.setVelocity(vel.add(xt * 0.4, 0.42, zt * 0.4));
           } else if (!mc.player.isOnGround() && !mc.player.isGliding()) {
              Vec3d vel = mc.player.getVelocity();
              double yaw = Math.toRadians(mc.player.getYaw());
              double xt = -Math.sin(yaw);
              double zt = Math.cos(yaw);
              Vec3d add = new Vec3d(xt, 0, zt).multiply(0.02);
              if (mc.player.input.hasForwardMovement() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed()) {
                 mc.player.setVelocity(vel.add(add.x, 0, add.z));
              }
              if (mc.player.getVelocity().y > 0) {
                 mc.player.setVelocity(mc.player.getVelocity().add(0, 0.002, 0));
              }
           }
       }
    }

    @EventInit
    public void onMovePost(EventOnMovePost e) {
       if (!this.enable || !mode.is("RW TEST")) return;
       if (mc.player == null) return;

       TimerManager.setTimer(1.7F);

       if (rwTicks > 3) {
          double bst = 0.03;
          if (rwTicks % 2 == 0) {
             mc.player.addVelocityInternal(new Vec3d(0, 0.03F, 0));
             if (mc.player.isOnGround()) {
                bst = 0.085;
             } else {
                bst = 0.03;
             }
          }

          double yaw = Math.toRadians(MoveUtil.getdir());
          double xt = -Math.sin(yaw);
          double zt = Math.cos(yaw);
          if (MoveUtil.getdir() == -1.0F) {
             xt = 0.0;
             zt = 0.0;
          }
          mc.player.addVelocityInternal(new Vec3d(xt * bst, 0, zt * bst));
       }

       rwTicks++;
    }

    @EventInit
    public void onMoveInput(EventMoveInput e) {
       if (!this.enable || !mode.is("RW TEST")) return;
       if (mc.player == null) return;

       if (mc.player.verticalCollision) rwGroundTicks++;
       else rwGroundTicks = 0;

       if (rwGroundTicks >= 1) mc.player.jump();
    }

    @EventInit
    public void onPostMotion(EventPostMotion e) {
       if (!this.enable || !mode.is("RW TEST")) return;
       if (mc.player == null) return;

       if (rwTicks % 2 == 0) {
          TimerManager.setTimer(0.3F);
          NetworkUtils.sendSilentPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
       }
    }

    @EventInit
    public void onPacket(EventPacket e) {
       if (!this.enable || !mode.is("RW TEST")) return;
       if (mc.player == null) return;

       if (e.getPacket() instanceof PlayerPositionLookS2CPacket) {
          if (rwTicks % 2 == 1) {
             rwTicks++;
          }

          TimerManager.setTimer(1.0F);
       }
    }

    @Override
    public void onDisable() {
       if (mode.is("RW TEST")) {
          TimerManager.setTimer(1.0F);
          rwTicks = 0;
          rwGroundTicks = 0;
       }
       super.onDisable();
    }

    private double[] getDirectionToPoint(Vec3d from, Vec3d to, double spd) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      double len = Math.sqrt(dx * dx + dz * dz);
      return len == 0.0 ? new double[]{0.0, 0.0} : new double[]{dx / len * spd, dz / len * spd};
   }
}
