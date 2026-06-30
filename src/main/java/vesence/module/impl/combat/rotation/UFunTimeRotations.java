package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static vesence.utils.other.IMinecraft.mc;

@Environment(EnvType.CLIENT)
public class UFunTimeRotations {
    private static float basePitch = 0.0f;
    private static final Random rand = new Random();
    private static float currentYawOffset = 0f;
    private static float currentPitchOffset = 0f;
    private static float targetYawOffset = 0f;
    private static float targetPitchOffset = 0f;
    private static int lastTargetId = -1;
    private static boolean wasAttacking = false;
    private static long nextOffsetTime = 0;
    private static final long OFFSET_INTERVAL_MIN = 7000L;
    private static final long OFFSET_INTERVAL_MAX = 10000L;
    private static final float OFFSET_DAMP = 0.8f;
    private static final float OFFSET_DAMP_INSTANT = 1.0f;
    private static final float MAX_STEP = 180f;
    private static final float STEP_INTERPOLATION = 0.55f;

    public static void rotation(LivingEntity target, boolean attack) {
        if (target != null && target.getId() != lastTargetId) {
            resetState(target);
        }

        Vec3d point = UBoxPoints.getBestVector3dOnEntityBox(target).subtract(mc.player.getEyePos());

        float baseYaw = (float) Math.toDegrees(Math.atan2(-point.x, point.z));
        basePitch = (float) MathHelper.clamp(
                -Math.toDegrees(Math.atan2(point.y, Math.hypot(point.x, point.z))),
                -90.0, 90.0
        );

        long now = System.currentTimeMillis();

        if (attack) {
            targetYawOffset = 0f;
            targetPitchOffset = 0f;
            currentYawOffset = 0f;
            currentPitchOffset = 0f;
            nextOffsetTime = 0;
        } else {
            if (wasAttacking && !attack) {
                generateOffset(now, true);
            } else if (now >= nextOffsetTime) {
                generateOffset(now, false);
            }

            float damp = (wasAttacking && !attack) ? OFFSET_DAMP_INSTANT : OFFSET_DAMP;
            currentYawOffset += (targetYawOffset - currentYawOffset) * damp;
            currentPitchOffset += (targetPitchOffset - currentPitchOffset) * damp;
        }
        wasAttacking = attack;

        float targetYaw = baseYaw + currentYawOffset;
        float targetPitch = basePitch + currentPitchOffset;
        targetPitch = MathHelper.clamp(targetPitch, -90f, 90f);

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        float hypot = (float) Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch));
        if (hypot < 1e-4f) hypot = 1e-4f;

        boolean isInOffset = Math.abs(currentYawOffset) > 0.5f || Math.abs(currentPitchOffset) > 0.5f;
        float speedBase = (attack || isInOffset)
                ? Mathf.randomValue(100, 130)
                : Mathf.randomValue(40, 60);

        float maxYawSpeed  = Math.abs(deltaYaw  / hypot) * speedBase;
        float maxPitchSpeed = Math.abs(deltaPitch / hypot) * speedBase;

        float shakeYaw   = (float) (randomRange(4, 7) * Math.sin(System.currentTimeMillis() / 60.0));
        float shakePitch = (float) (randomRange(3, 7) * Math.cos(System.currentTimeMillis() / 60.0));

        float stepYaw = MathHelper.lerp(STEP_INTERPOLATION, 0f, deltaYaw);
        float stepPitch = MathHelper.lerp(STEP_INTERPOLATION, 0f, deltaPitch);
        stepYaw = Math.signum(stepYaw) * Math.min(Math.abs(stepYaw), MAX_STEP);
        stepPitch = Math.signum(stepPitch) * Math.min(Math.abs(stepPitch), MAX_STEP);

        float newYaw = currentYaw + stepYaw;
        basePitch = currentPitch + stepPitch;

        int age = mc.player.age;
        if (age % 15 == 0 && age > 0) {
            basePitch += -4.0f;
        }

        Rotation rot = new Rotation(newYaw + shakeYaw, MathHelper.clamp(basePitch + shakePitch, -90f, 90f));
        float yRet = Mathf.randomValue(22.0f, 32.0f);
        float pRet = yRet * 0.85f;
        URotations.update(rot, maxYawSpeed, maxPitchSpeed,
                yRet, pRet, 0, 15, false);
    }

    private static void generateOffset(long now, boolean postAttack) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        if (postAttack) {
            targetYawOffset = rng.nextFloat(-70f, 70f);
            targetPitchOffset = rng.nextFloat(-60f, 60f);
        } else {
            if (rng.nextBoolean()) {
                targetYawOffset = rng.nextFloat(-180f, 180f);
                targetPitchOffset = rng.nextFloat(-90f, 90f);
            } else {
                targetYawOffset = rng.nextFloat(-20f, 20f);
                targetPitchOffset = rng.nextFloat(-15f, 15f);
            }
        }

        nextOffsetTime = now + OFFSET_INTERVAL_MIN
                + rng.nextInt((int)(OFFSET_INTERVAL_MAX - OFFSET_INTERVAL_MIN));
    }

    private static void resetState(LivingEntity target) {
        currentYawOffset = 0f;
        currentPitchOffset = 0f;
        targetYawOffset = 0f;
        targetPitchOffset = 0f;
        wasAttacking = false;
        nextOffsetTime = System.currentTimeMillis() + OFFSET_INTERVAL_MIN
                + ThreadLocalRandom.current().nextInt((int)(OFFSET_INTERVAL_MAX - OFFSET_INTERVAL_MIN));
        lastTargetId = target != null ? target.getId() : -1;
    }

    private static float randomRange(int min, int max) {
        return min + rand.nextInt(max - min + 1);
    }

    public static void state() {
        basePitch = mc.player.getPitch();
    }
}