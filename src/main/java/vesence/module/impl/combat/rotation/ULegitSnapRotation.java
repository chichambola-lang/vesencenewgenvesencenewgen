package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.Vesence;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;

import static vesence.utils.other.IMinecraft.mc;

@Environment(EnvType.CLIENT)
public class ULegitSnapRotation {
    static AttackAura aura = Vesence.get.getManager().get(AttackAura.class);

    public static void rotation(LivingEntity target, boolean attack) {
        Vec3d aimPoint = UBoxPoints.getClosestRotationPoint(target, true);
        float[] angles = getAnglesTo(aimPoint);
        float targetYaw = angles[0];
        float targetPitch = angles[1];
        float baseYaw = FreeLookUtil.freeYaw;
        float basePitch = FreeLookUtil.freePitch;

        if (attack) {
            float fovDeg = aura.fovValue.get().floatValue();
            boolean inFov = isTargetInFov(target, fovDeg);
            float angleDist = getAngleDistance(targetYaw, targetPitch);

            if (inFov) {
                if (angleDist <= fovDeg) {
                    baseYaw = angles[0];
                    basePitch = angles[1];
                }
            }
        }

        Rotation rotation = new Rotation(baseYaw, basePitch);
        URotations.update(rotation, Mathf.randomValue(150, 180), Mathf.randomValue(150, 180), Mathf.randomValue(150, 180), Mathf.randomValue(150, 180), 0, 1, false);
    }

    private static float[] getAnglesTo(Vec3d point) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d dir = point.subtract(eyes);
        double dist = dir.length();
        if (dist < 1e-4) dist = 1e-4;

        double dx = dir.x / dist;
        double dy = dir.y / dist;
        double dz = dir.z / dist;

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
        return new float[]{yaw, pitch};
    }

    private static boolean isTargetInFov(LivingEntity target, float halfFov) {
        Vec3d eyes = mc.player.getEyePos();
        Box box = target.getBoundingBox();

        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double[] checkY = {box.minY, (box.minY + box.maxY) / 2.0, box.maxY};

        for (double y : checkY) {
            Vec3d toPoint = new Vec3d(cx, y, cz).subtract(eyes);
            double len = toPoint.length();
            if (len < 1e-4) return true;

            Vec3d dir = toPoint.multiply(1.0 / len);
            float pYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pPitch = (float) (-Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));

            float dYaw = Math.abs(MathHelper.wrapDegrees(pYaw - mc.player.getYaw()));
            float dPitch = Math.abs(MathHelper.wrapDegrees(pPitch - mc.player.getPitch()));

            if (dYaw <= halfFov && dPitch <= halfFov) {
                return true;
            }
        }
        return false;
    }

    private static float getAngleDistance(float targetYaw, float targetPitch) {
        float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - mc.player.getYaw()));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(targetPitch - mc.player.getPitch()));
        return (float) Math.hypot(yawDiff, pitchDiff);
    }
}
