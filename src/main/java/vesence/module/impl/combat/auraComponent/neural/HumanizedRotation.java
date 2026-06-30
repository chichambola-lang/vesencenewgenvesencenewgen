package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.utils.other.IMinecraft;
import vesence.utils.player.PlayerUtil;

@Environment(EnvType.CLIENT)
public final class HumanizedRotation implements IMinecraft {
   private static float serverYaw = 0f;
   private static float serverPitch = 0f;
   private static boolean initialized = false;

   private static int ticksAiming = 0;

   private static float[] targetLocal = null;
   private static float[] currentLocal = null;
   private static int aimBaseTicksLeft = 0;
   private static final int AIM_BASE_REFRESH = 20;
   private static final float AIM_BASE_LERP = 0.12f;

   private static boolean returning = false;
   private static int returnTicks = 0;
   private static final int RETURN_DURATION = 12;

   private static AimBrain brain;
   private static boolean stopped = true;

   private static final HitboxWaypoints.PathState wpState = new HitboxWaypoints.PathState();
   private static boolean waypointsAttempted = false;

   private static final float MIN_YAW_STEP = 0.6f;
   private static final float MIN_PITCH_STEP = 0.5f;
   private static final float RL_HARD_MAX_YAW = 30.0f;
   private static final float RL_HARD_MAX_YAW_ATTACK = 40.0f;
   private static final float RL_HARD_MAX_PITCH = 25.0f;
   private static final float RL_HARD_MAX_PITCH_ATTACK = 32.0f;
   private static final float ACTION_SCALE_MIN = 0.2f;
   private static final float ACTION_SCALE_STEP = 0.2f;

   private static boolean captureMode = false;
   private static boolean rlEnabled = true;
   private static float lastObservedYaw = 0f;
   private static float lastObservedPitch = 0f;
   private static boolean hasLastObserved = false;
   private static int captureStateFlushCounter = 0;

   private static float[] prevState = null;
   private static int prevAction = 12;
   private static float prevDistToTarget = 0f;
   private static float prevTargetHealth = 1f;
   private static boolean hasPrevObservation = false;

   public static void initBrain() {
      initBrain(ExpertCapture.currentServerId());
   }

   public static void initBrain(String serverId) {
      ExpertCapture.setActiveServerId(serverId);
      brain = new AimBrain();
      try {
         brain.setSaveFile(ExpertCapture.brainFile(ExpertCapture.getActiveServerId()));
      } catch (Exception ignored) {
      }
      if (brain != null) {
         brain.loadAfterInit();
         brain.resetTarget();
      }
      prevState = null;
      hasPrevObservation = false;
   }

   public static boolean isCaptureMode() {
      return captureMode;
   }

   public static void setCaptureMode(boolean enabled) {
      if (enabled == captureMode) return;
      captureMode = enabled;
      hasLastObserved = false;
      if (enabled) {
         FreeLookUtil.active = false;
         returning = false;
         returnTicks = 0;
         serverYaw = mc.player != null ? mc.player.headYaw : serverYaw;
         serverPitch = mc.player != null ? mc.player.getPitch() : serverPitch;
      }
   }

   public static void setRLEnabled(boolean enabled) {
      rlEnabled = enabled;
   }

   public static boolean isRLEnabled() {
      return rlEnabled;
   }

   public static AimBrain getBrain() {
      return brain;
   }

   public static void reset() {
      ticksAiming = 0;
      initialized = false;
      returning = false;
      returnTicks = 0;

      targetLocal = null;
      currentLocal = null;
      aimBaseTicksLeft = 0;

      wpState.reset();
      waypointsAttempted = false;
      HitboxWaypoints.resetAll();

      if (brain != null) brain.resetTarget();
      prevState = null;
      hasPrevObservation = false;
   }

   public static void stop() {
      if (captureMode) {
         stopped = true;
         FreeLookUtil.active = false;
         reset();
         return;
      }
      if (brain != null && !stopped) {
         brain.saveToFile();
      }
      stopped = true;
      if (FreeLookUtil.active && mc.player != null) {
         mc.player.setYaw(mc.player.headYaw = FreeLookUtil.freeYaw);
         mc.player.setPitch(FreeLookUtil.freePitch);
         mc.player.bodyYaw = PlayerUtil.calculateCorrectYawOffset(FreeLookUtil.freeYaw);
      }
      FreeLookUtil.active = false;
      reset();
   }

   public static void stopSmooth() {
      if (captureMode) {
         stop();
         return;
      }
      if (FreeLookUtil.active && initialized) {
         returning = true;
         returnTicks = 0;
      } else {
         stop();
      }
   }

   public static boolean tickReturn() {
      if (captureMode) return false;
      if (!returning || mc.player == null) return false;

      float targetYaw = FreeLookUtil.freeYaw;
      float targetPitch = FreeLookUtil.freePitch;

      float t = Math.min((float) returnTicks / RETURN_DURATION, 1.0f);
      float eased = easeInOutCubic(t);

      float dy = MathHelper.wrapDegrees(targetYaw - serverYaw);
      float dp = targetPitch - serverPitch;

      serverYaw += GCDUtil.getSensitivity(dy * eased);
      serverPitch = MathHelper.clamp(serverPitch + GCDUtil.getSensitivity(dp * eased), -90.0f, 90.0f);

      mc.player.setYaw(mc.player.headYaw = serverYaw);
      mc.player.setPitch(serverPitch);
      mc.player.bodyYaw = PlayerUtil.calculateCorrectYawOffset(serverYaw);

      returnTicks++;
      if (t >= 1.0f) {
         FreeLookUtil.active = false;
         reset();
         return false;
      }
      return true;
   }

   public static boolean isReturning() {
      return returning;
   }

   public static boolean isStopped() {
      return stopped;
   }

   public static void computeCapture(LivingEntity target, boolean isAttack) {
      if (mc.player == null || target == null) return;
      if (FreeLookUtil.active) {
         ExpertCapture.markFreeLookHijack();
         FreeLookUtil.active = false;
      }
      if (brain == null) {
         initBrain(ExpertCapture.currentServerId());
      }

      captureStateFlushCounter++;
      if (captureStateFlushCounter % 3 != 0) {
         lastObservedYaw = mc.player.headYaw;
         lastObservedPitch = mc.player.getPitch();
         hasLastObserved = true;
         return;
      }

      Box box = target.getBoundingBox();
      double boxW = box.maxX - box.minX;
      double boxH = box.maxY - box.minY;
      Vec3d center = target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0);

      aimBaseTicksLeft--;
      if (targetLocal == null || aimBaseTicksLeft <= 0) {
         Vec3d fresh = UBoxPoints.getBestVector3dOnEntityBox(box, false);
         if (fresh != null) {
            targetLocal = new float[]{
               (float)(fresh.x - center.x),
               (float)(fresh.y - center.y),
               (float)(fresh.z - center.z)
            };
         }
         aimBaseTicksLeft = AIM_BASE_REFRESH;
      }
      if (currentLocal == null) {
         currentLocal = targetLocal != null
            ? new float[]{targetLocal[0], targetLocal[1], targetLocal[2]}
            : new float[]{0f, 0f, 0f};
      } else if (targetLocal != null) {
         currentLocal[0] += (targetLocal[0] - currentLocal[0]) * AIM_BASE_LERP;
         currentLocal[1] += (targetLocal[1] - currentLocal[1]) * AIM_BASE_LERP;
         currentLocal[2] += (targetLocal[2] - currentLocal[2]) * AIM_BASE_LERP;
      }
      float halfW = (float) boxW * 0.48f;
      float halfH = (float) boxH * 0.48f;
      currentLocal[0] = MathHelper.clamp(currentLocal[0], -halfW, halfW);
      currentLocal[1] = MathHelper.clamp(currentLocal[1], -halfH, halfH);
      currentLocal[2] = MathHelper.clamp(currentLocal[2], -halfW, halfW);
      Vec3d aimBase = center.add(currentLocal[0], currentLocal[1], currentLocal[2]);

      Vec3d eyePos = mc.player.getEyePos();
      Vec3d toCenter = aimBase.subtract(eyePos);
      float yawToCenter = (float) Math.toDegrees(Math.atan2(-toCenter.x, toCenter.z));

      Vec3d aimVecRaw = aimBase.subtract(eyePos);
      float targetYawRaw = (float) Math.toDegrees(Math.atan2(-aimVecRaw.x, aimVecRaw.z));
      float targetPitchRaw = (float) MathHelper.clamp(
         -Math.toDegrees(Math.atan2(aimVecRaw.y, Math.hypot(aimVecRaw.x, aimVecRaw.z))),
         -90.0f, 90.0f
      );
      float deltaYawRaw = MathHelper.wrapDegrees(targetYawRaw - (hasLastObserved ? lastObservedYaw : mc.player.headYaw));
      float deltaPitchRaw = targetPitchRaw - (hasLastObserved ? lastObservedPitch : mc.player.getPitch());
      float absDistRaw = (float) Math.sqrt(deltaYawRaw * deltaYawRaw + deltaPitchRaw * deltaPitchRaw);

      float distToTarget = (float) mc.player.getEyePos().distanceTo(aimBase);
      float aimVelocity = absDistRaw;
      float ticksNorm = Math.min(ticksAiming / 200.0f, 1.0f);

      float myHealth = mc.player.getHealth() / mc.player.getMaxHealth();
      float myArmor = mc.player.getArmor() / 20.0f;
      float targetHealth = target.getHealth() / target.getMaxHealth();
      float targetArmor = target.getArmor() / 20.0f;

      Vec3d targetVel = target.getVelocity();
      float targetVelX = (float) targetVel.x;
      float targetVelZ = (float) targetVel.z;

      if (brain != null) {
         brain.extractFeatures(target, isAttack, distToTarget, 0f, aimVelocity, ticksAiming,
                              deltaYawRaw, deltaPitchRaw,
                              myHealth, myArmor, targetHealth, targetArmor,
                              targetVelX, targetVelZ);
      }

      float currentYaw = mc.player.headYaw;
      float currentPitch = mc.player.getPitch();
      float dy = 0.0f;
      float dp = 0.0f;
      if (hasLastObserved) {
         dy = MathHelper.wrapDegrees(currentYaw - lastObservedYaw);
         dp = currentPitch - lastObservedPitch;
      }
      lastObservedYaw = currentYaw;
      lastObservedPitch = currentPitch;
      hasLastObserved = true;

      float rawMouseYaw = MouseDeltas.rawYawFloat();
      float rawMousePitch = MouseDeltas.rawPitchFloat();
      ExpertCapture.pushRing(dy, dp, rawMouseYaw, rawMousePitch);
      ExpertCapture.pushRawWindow(rawMouseYaw, rawMousePitch);

      float[] lastInputs = brain != null ? brain.getLastInputs() : null;
      if (lastInputs != null && ExpertCapture.isRecording()) {
         float[] rawWindow = ExpertCapture.getWindowContext();
         for (int i = 0; i < 5; i++) {
            lastInputs[33 + i] = MathHelper.clamp((rawWindow[i] + 2.0f) / 4.0f, 0f, 1f);
            lastInputs[38 + i] = MathHelper.clamp((rawWindow[5 + i] + 2.0f) / 4.0f, 0f, 1f);
         }
         boolean hit = isAttack && distToTarget < 4.0f;
         float aimX = currentLocal != null ? currentLocal[0] : 0f;
         float aimY = currentLocal != null ? currentLocal[1] : 0f;
         float aimZ = currentLocal != null ? currentLocal[2] : 0f;
         ExpertCapture.record(lastInputs, dy, dp, rawMouseYaw, rawMousePitch, hit, target.getId(), aimX, aimY, aimZ);
      }

      if (ExpertCapture.isDebug()) {
         String dbg = "yaw=" + String.format("%.2f", currentYaw)
            + " headYaw=" + String.format("%.2f", mc.player.headYaw)
            + " bodyYaw=" + String.format("%.2f", mc.player.bodyYaw)
            + " dy=" + String.format("%.3f", dy)
            + " dp=" + String.format("%.3f", dp)
            + " rawYaw=" + String.format("%.3f", rawMouseYaw)
            + " rawPitch=" + String.format("%.3f", rawMousePitch)
            + " FL.active=" + FreeLookUtil.active
            + " canRot=" + MouseDeltas.lastChangeLookCalled
            + " evCancel=" + MouseDeltas.lastEventCancelled;
         ExpertCapture.maybeLogDebug(dbg);
      }

      ticksAiming++;
   }

   public static void compute(LivingEntity target, boolean isAttack) {
      if (mc.player == null) return;
      if (captureMode) {
         computeCapture(target, isAttack);
         return;
      }
      if (brain == null) {
         initBrain(ExpertCapture.currentServerId());
      }

      returning = false;

      if (!initialized) {
         serverYaw = mc.player.headYaw;
         serverPitch = mc.player.getPitch();
         initialized = true;
         stopped = false;
      }

      if (!FreeLookUtil.active) {
         FreeLookUtil.active = true;
      }

      Box box = target.getBoundingBox();
      double boxW = box.maxX - box.minX;
      double boxH = box.maxY - box.minY;
      Vec3d center = target.getEntityPos().add(0.0, target.getHeight() * 0.5, 0.0);

      if (!waypointsAttempted && brain != null) {
         waypointsAttempted = true;
         HitboxWaypoints.load(ExpertCapture.captureFile(ExpertCapture.getActiveServerId()));
      }

      Vec3d aimBase;
      if (HitboxWaypoints.isLoaded() && wpState.active && HitboxWaypoints.size() >= 2) {
         aimBase = center;
      } else {
         aimBaseTicksLeft--;
         if (targetLocal == null || aimBaseTicksLeft <= 0) {
            Vec3d fresh = UBoxPoints.getBestVector3dOnEntityBox(box, false);
            if (fresh != null) {
               targetLocal = new float[]{
                  (float)(fresh.x - center.x),
                  (float)(fresh.y - center.y),
                  (float)(fresh.z - center.z)
               };
            }
            aimBaseTicksLeft = AIM_BASE_REFRESH;
         }
         if (currentLocal == null) {
            currentLocal = targetLocal != null
               ? new float[]{targetLocal[0], targetLocal[1], targetLocal[2]}
               : new float[]{0f, 0f, 0f};
         } else if (targetLocal != null) {
            currentLocal[0] += (targetLocal[0] - currentLocal[0]) * AIM_BASE_LERP;
            currentLocal[1] += (targetLocal[1] - currentLocal[1]) * AIM_BASE_LERP;
            currentLocal[2] += (targetLocal[2] - currentLocal[2]) * AIM_BASE_LERP;
         }
         float halfW = (float) boxW * 0.48f;
         float halfH = (float) boxH * 0.48f;
         currentLocal[0] = MathHelper.clamp(currentLocal[0], -halfW, halfW);
         currentLocal[1] = MathHelper.clamp(currentLocal[1], -halfH, halfH);
         currentLocal[2] = MathHelper.clamp(currentLocal[2], -halfW, halfW);
         aimBase = center.add(currentLocal[0], currentLocal[1], currentLocal[2]);
      }

      Vec3d eyePos = mc.player.getEyePos();
      Vec3d aimVecRaw = aimBase.subtract(eyePos);
      float targetYawRaw = (float) Math.toDegrees(Math.atan2(-aimVecRaw.x, aimVecRaw.z));
      float targetPitchRaw = (float) MathHelper.clamp(
         -Math.toDegrees(Math.atan2(aimVecRaw.y, Math.hypot(aimVecRaw.x, aimVecRaw.z))),
         -90.0f, 90.0f
      );
      float deltaYawRaw = MathHelper.wrapDegrees(targetYawRaw - serverYaw);
      float deltaPitchRaw = targetPitchRaw - serverPitch;
      float absDistRaw = (float) Math.sqrt(deltaYawRaw * deltaYawRaw + deltaPitchRaw * deltaPitchRaw);

      float distToTarget = (float) mc.player.getEyePos().distanceTo(aimBase);
      float aimVelocity = absDistRaw;
      float ticksNorm = Math.min(ticksAiming / 200.0f, 1.0f);
      float aimProgress = Math.min(ticksAiming / Math.max(absDistRaw / 15.0f, 12.0f), 1.0f);

      float myHealth = mc.player.getHealth() / mc.player.getMaxHealth();
      float myArmor = mc.player.getArmor() / 20.0f;
      float targetHealth = target.getHealth() / target.getMaxHealth();
      float targetArmor = target.getArmor() / 20.0f;

      Vec3d targetVel = target.getVelocity();
      float targetVelX = (float) targetVel.x;
      float targetVelZ = (float) targetVel.z;

      float[] state = brain != null
         ? brain.extractFeatures(target, isAttack, distToTarget, aimProgress, aimVelocity, ticksAiming,
                                 deltaYawRaw, deltaPitchRaw,
                                 myHealth, myArmor, targetHealth, targetArmor,
                                 targetVelX, targetVelZ)
         : new float[RLAgent.STATE_SIZE];

      int action = brain != null && rlEnabled ? brain.selectAction(state) : (QNetwork.NUM_ACTIONS / 2);
      int yawScaleIdx = MathHelper.clamp(action / 5, 0, 4);
      int pitchScaleIdx = MathHelper.clamp(action % 5, 0, 4);

      float maxDy = isAttack ? RL_HARD_MAX_YAW_ATTACK : RL_HARD_MAX_YAW;
      float maxDp = isAttack ? RL_HARD_MAX_PITCH_ATTACK : RL_HARD_MAX_PITCH;

      float yawScale = ACTION_SCALE_MIN + yawScaleIdx * ACTION_SCALE_STEP;
      float pitchScale = ACTION_SCALE_MIN + pitchScaleIdx * ACTION_SCALE_STEP;

      float finalDeltaYaw = scaleTowardTarget(deltaYawRaw, yawScale, maxDy, MIN_YAW_STEP);
      float finalDeltaPitch = scaleTowardTarget(deltaPitchRaw, pitchScale, maxDp, MIN_PITCH_STEP);

      finalDeltaYaw = MathHelper.clamp(finalDeltaYaw, -maxDy, maxDy);
      finalDeltaPitch = MathHelper.clamp(finalDeltaPitch, -maxDp, maxDp);

      float gcdYaw = GCDUtil.getSensitivity(finalDeltaYaw);
      float gcdPitch = GCDUtil.getSensitivity(finalDeltaPitch);

      serverYaw += gcdYaw;
      serverPitch = MathHelper.clamp(serverPitch + gcdPitch, -90.0f, 90.0f);

      mc.player.setYaw(mc.player.headYaw = serverYaw);
      mc.player.setPitch(serverPitch);
      mc.player.bodyYaw = PlayerUtil.calculateCorrectYawOffset(serverYaw);

      if (brain != null && rlEnabled) {
         float targetVelocity = (float) Math.sqrt(targetVelX * targetVelX + targetVelZ * targetVelZ);
         boolean hit = isAttack && distToTarget < 3.0f && absDistRaw < 5.0f;
         boolean done = target.isDead() || target.getHealth() <= 0.0f || mc.player.isDead();

         float reward = brain.computeReward(isAttack, distToTarget, (float) boxW, (float) boxH,
                                            myHealth, targetHealth, aimVelocity, targetVelocity, hit);

         if (hasPrevObservation && prevState != null) {
            brain.observe(prevState, prevAction, reward, state, done);
         }

         if (!done) {
            prevState = state;
            prevAction = action;
            prevDistToTarget = distToTarget;
            prevTargetHealth = targetHealth;
            hasPrevObservation = true;
         } else {
            prevState = null;
            hasPrevObservation = false;
         }

         brain.finishStep(isAttack, distToTarget, targetHealth);
      }

      ticksAiming++;
   }

   private static float easeInOutCubic(float t) {
      return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
   }

   private static float scaleTowardTarget(float targetDelta, float scale, float maxStep, float minStep) {
      float absDelta = Math.abs(targetDelta);
      if (absDelta <= 0.001f) {
         return 0.0f;
      }

      if (absDelta <= minStep) {
         return targetDelta;
      }

      float scaled = absDelta * scale;
      scaled = MathHelper.clamp(scaled, minStep, Math.min(absDelta, maxStep));
      return Math.copySign(scaled, targetDelta);
   }

   private HumanizedRotation() {
   }
}
