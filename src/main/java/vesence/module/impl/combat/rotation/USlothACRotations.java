package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.RayTraceUtil;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static vesence.utils.other.IMinecraft.mc;

@Environment(EnvType.CLIENT)
public class USlothACRotations {
    private static float basePitch = 0.0f;
    private static final AtomicLong время = new AtomicLong(0);
    private static long startTime = System.currentTimeMillis();

    public static void rotation(LivingEntity target, boolean attack, float distance) {
        if (target == null) {
            if (время.get() == 0) {
                время.set(System.currentTimeMillis());
            }

            if (System.currentTimeMillis() - время.get() < 3000) {
                float idlePitch = (float) (0 * Math.cos(System.currentTimeMillis() / 10F));
                float idleYaw   = (float) (0 * Math.sin(System.currentTimeMillis() / 10F));

                float yaw   = mc.player.getYaw() + idleYaw;
                float pitch = MathHelper.clamp(mc.player.getPitch() + idlePitch, -90.0f, 90.0f);
                URotations.update(new Rotation(yaw, pitch), Mathf.randomValue(70, 90), Mathf.randomValue(10, 16), 30.0F, 30.0F, 1, 15, false);
            }
            return;
        } else {
            время.set(0);
        }

        Vec3d point = UBoxPoints.getBestVector3dOnEntityBox(target).subtract(mc.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-point.x, point.z));
        basePitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(point.y, Math.hypot(point.x, point.z))),
                -90.0, 90.0
        );

        float currentYaw   = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float targetYaw   = yaw;
        float targetPitch = basePitch;

        float yawDelta   = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = targetPitch - currentPitch;
        float rotationDifference = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

        boolean атака = attack;
        boolean pa = target != null && RayTraceUtil.rayTraceEntity(currentYaw, currentPitch, distance, target);

        float pitchOsc = 0;
        if (target != null && !атака) {
            pitchOsc = (float) (360 * Math.cos(System.currentTimeMillis() / 31.5D));
        }

        float yawOsc = 0;
        if (target != null && !атака) {
            yawOsc = (float) (360 * Math.sin(System.currentTimeMillis() / 28.7D));
        }

        float скорост = атака ? 0.67F : (attack ? 1F : 0.67F);
        if (атака && !pa) {
            скорост = 0.67F;
        }

        float lineYaw   = rotationDifference != 0 ? (Math.abs(yawDelta / rotationDifference) * Mathf.randomInt(110, 140)) : 0;
        float linePitch = (Math.abs(pitchDelta) * Mathf.randomInt(110, 140));
        float moveYaw   = MathHelper.clamp(yawDelta, -lineYaw, lineYaw);
        float movePitch = MathHelper.clamp(pitchDelta, -linePitch, linePitch);
        float targetYawResult = currentYaw + moveYaw;
        float lerpFactor = MathHelper.clamp(randomLerp(скорост, скорост + 0.2F), 0f, 1f);
        long now = System.currentTimeMillis();
        float elapsed = (now - startTime) / 100.0f;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        float jitterYaw = (float) Math.ceil(MathHelper.cos(elapsed) * rng.nextFloat(12f, 24f));
        float jitterPitch = (float) Math.ceil(MathHelper.sin(elapsed) * rng.nextFloat(2f, 4f));

        float yawRandomOffset = 0;
        if (target != null && !атака) {
            yawRandomOffset = (float) (Math.sin(now / 15.3D) * Math.cos(now / 8.7D) * rng.nextFloat(45f, 90f));
        }

        float finalYaw = MathHelper.lerp(lerpFactor, currentYaw, targetYawResult) + yawOsc + jitterYaw + yawRandomOffset;
        float calculatedPitch = MathHelper.lerp(
                lerpFactor,
                currentPitch,
                currentPitch + movePitch
        ) + pitchOsc;

        float finalPitch = MathHelper.clamp(calculatedPitch, -90.0f, 90.0f) + jitterPitch;

        URotations.update(new Rotation(finalYaw, finalPitch), lineYaw, linePitch, 30.0F, 30.0F, 1, 15, false);
    }

    private static float randomLerp(float min, float max) {
        return MathHelper.lerp(Mathf.random(0F, 1F), min, max);
    }

    public static void state() {
        basePitch = mc.player.getPitch();
    }
}