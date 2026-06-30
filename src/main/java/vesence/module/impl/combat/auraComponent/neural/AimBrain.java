package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.io.File;

@Environment(EnvType.CLIENT)
public final class AimBrain {
   public static final int INPUT_SIZE = 43;

   private final float[] lastInputs = new float[INPUT_SIZE];
   private final float[] prevState = new float[INPUT_SIZE];
   private int prevAction = QNetwork.NUM_ACTIONS / 2;
   private float prevDistToTarget = 0f;
   private float prevTargetHealth = 1f;
   private boolean hasPrev = false;

   private final float[][] contextBuffer = new float[10][2];
   private int contextIndex = 0;

   private float currentOffsetH = 0f;
   private float currentOffsetV = 0f;
   private float targetOffsetH = 0f;
   private float targetOffsetV = 0f;

   private File brainFile;
   private final HistoryManager history = new HistoryManager();
   private RLAgent agent;
   private boolean initLogSent = false;

   private final SmoothingFilter velocityHFilter = new SmoothingFilter(0.3f);
   private final SmoothingFilter velocityVFilter = new SmoothingFilter(0.3f);
   private final SmoothingFilter speedFilter = new SmoothingFilter(0.25f);
   private final SmoothingFilter aggressionFilter = new SmoothingFilter(0.4f);
   private final SmoothingFilter jitterFilter = new SmoothingFilter(0.3f);

   private float cachedDistance = 0f;
   private float cachedTargetVelocity = 0f;

   public AimBrain() {
   }

   public void loadAfterInit() {
      history.load();
      if (brainFile != null && agent == null) {
         agent = RLAgent.load(brainFile);
         if (agent == null) {
            agent = new RLAgent(ExpertCapture.getActiveServerId());
         }
      }
      boolean hasData = history.size() > 0 || agent != null;
      logInit(hasData);
   }

   private void logInit(boolean loaded) {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.player == null) return;
      initLogSent = true;
      if (loaded) {
         mc.player.sendMessage(Text.of(
            "\u00a77[\u00a7bAI\u00a77] \u00a7fЗагружено: \u00a7a" + history.getTotalBattles() +
            " боёв\u00a7f, \u00a7a" + history.getTotalHits() + " попаданий\u00a7f" +
            (agent != null ? " | RL: \u00a7aобучен (ε=" + String.format("%.3f", agent.getEpsilon()) + ")" : " | RL: \u00a7eне обучен")
         ), false);
      } else {
         mc.player.sendMessage(Text.of("\u00a77[\u00a7bAI\u00a77] \u00a7fИнициализация с нуля. Набери историю и сделай .ac train"), false);
      }
   }

   public void setSaveFile(File file) {
      this.brainFile = file;
      String name = file.getName();
      int dot = name.lastIndexOf('.');
      if (dot > 0) {
         name = name.substring(0, dot);
      }
      history.setFile(new File(file.getParent(), name + "_history.bin"));
   }

   public void freezeRL() {
   }

   public boolean isBCMode() {
      return agent != null;
   }

   public boolean isRLFrozen() {
      return false;
   }

   public void warmupFromHistory(HistoryManager hist) {
      if (agent == null) {
         agent = new RLAgent(ExpertCapture.getActiveServerId());
      }
      agent.warmupFromHistory(hist);
   }

   public void trainStep() {
      if (agent != null) agent.trainStep();
   }

   public void trainMany(int epochs) {
      if (agent == null) return;
      for (int i = 0; i < epochs; i++) agent.trainStep();
   }

   public float[] extractFeatures(LivingEntity target, boolean isAttack, float distToTarget,
                                   float aimProgress, float aimVelocity, int ticksAiming,
                                   float deltaYawNorm, float deltaPitchNorm,
                                   float myHealth, float myArmor,
                                   float targetHealth, float targetArmor,
                                   float targetVelX, float targetVelZ) {
      cachedDistance = distToTarget;
      cachedTargetVelocity = (float) Math.sqrt(targetVelX * targetVelX + targetVelZ * targetVelZ);

      lastInputs[0] = clamp01(distToTarget / 6.0f);
      lastInputs[1] = clamp01(aimProgress);
      lastInputs[2] = isAttack ? 1.0f : 0.0f;
      lastInputs[3] = clamp01(aimVelocity / 30.0f);
      lastInputs[4] = clamp01(Math.min(ticksAiming / 200.0f, 1.0f));
      lastInputs[5] = clamp01(Math.abs(deltaYawNorm) / 180.0f);
      lastInputs[6] = clamp01(Math.abs(deltaPitchNorm) / 90.0f);
      lastInputs[7] = clamp01((targetVelX + 1.0f) * 0.5f);
      lastInputs[8] = clamp01((targetVelZ + 1.0f) * 0.5f);

      for (int i = 0; i < 10; i++) {
         int idx = (contextIndex - 1 - i + 20) % 10;
         lastInputs[9 + i] = (contextBuffer[idx][0] + 1.0f) * 0.5f;
      }

      lastInputs[19] = clamp01(myHealth);
      lastInputs[20] = clamp01(myArmor);
      lastInputs[21] = clamp01(targetHealth);
      lastInputs[22] = clamp01(targetArmor / 20.0f);

      for (int i = 23; i < 33; i++) lastInputs[i] = 0.5f;

      float[] rawWindow = ExpertCapture.getWindowContext();
      for (int i = 0; i < 5; i++) {
         lastInputs[33 + i] = clamp01((rawWindow[i] + 2.0f) / 4.0f);
         lastInputs[38 + i] = clamp01((rawWindow[5 + i] + 2.0f) / 4.0f);
      }

      return lastInputs.clone();
   }

   public int selectAction(float[] state) {
      if (agent == null) return QNetwork.NUM_ACTIONS / 2;
      return agent.selectAction(state);
   }

   public int selectGreedy(float[] state) {
      if (agent == null) return QNetwork.NUM_ACTIONS / 2;
      return agent.selectGreedy(state);
   }

   public void observe(float[] state, int action, float reward, float[] nextState, boolean done) {
      if (agent == null) {
         agent = new RLAgent(ExpertCapture.getActiveServerId());
      }
      agent.observe(state, action, reward, nextState, done);
   }

   public void storeTransition(float[] state, int action, float reward, boolean hit, boolean isAttack) {
      history.addTransition(state, action, reward, hit, isAttack);
   }

   public float computeReward(boolean isAttack, float distToTarget, float boxW, float boxH,
                              float myHealth, float targetHealth, float aimVelocity,
                              float targetVelocity, boolean hit) {
      float r = -0.01f;
      if (hasPrev) {
         float distDelta = prevDistToTarget - distToTarget;
         r += Math.max(-0.5f, Math.min(0.5f, distDelta * 0.1f));

         float healthDelta = prevTargetHealth - targetHealth;
         r += Math.max(0f, healthDelta) * 5.0f;

         r -= 0.005f;
      }
      if (distToTarget < 3.0f) r += 0.05f;
      if (myHealth < 0.2f) r -= 0.05f;
      if (aimVelocity > 0 && aimVelocity < 1.0f) r += 0.02f;
      if (hit) r += 1.0f;
      r -= Math.min(targetVelocity * 0.02f, 0.05f);
      return r;
   }

   public void finishStep(boolean isAttack, float distToTarget, float targetHealth) {
      prevDistToTarget = distToTarget;
      prevTargetHealth = targetHealth;
      hasPrev = true;
   }

   public float getVelocityH() { return velocityHFilter.filter(currentOffsetH); }
   public float getVelocityV() { return velocityVFilter.filter(currentOffsetV); }
   public float getSpeedMultiplier() { return speedFilter.filter(1.0f); }
   public float getAggression() { return aggressionFilter.filter(0.5f); }
   public float getJitterToggle() { return jitterFilter.filter(0.2f); }
   public float getSpeed() { return 45.0f; }
   public float getJitterMult() { return 0.0f; }
   public float getOvershootTendency() { return 0.0f; }
   public float getOuTheta() { return 0.85f; }
   public float getOuSigma() { return 0.3f; }
   public float getPerlinFreq() { return 0.25f; }

   public void accumulate(float boxW, float boxH) {
      float maxH = boxW * 0.4f;
      float maxV = boxH * 0.35f;
      targetOffsetH *= 0.998f;
      targetOffsetV *= 0.998f;
      targetOffsetH = MathHelper.clamp(targetOffsetH, -maxH, maxH);
      targetOffsetV = MathHelper.clamp(targetOffsetV, -maxV, maxV);
      currentOffsetH += (targetOffsetH - currentOffsetH) * 0.1f;
      currentOffsetV += (targetOffsetV - currentOffsetV) * 0.1f;
   }

   public float getCurrentOffsetH() { return currentOffsetH; }
   public float getCurrentOffsetV() { return currentOffsetV; }

   public float[] getLastInputs() {
      return lastInputs;
   }

   public void updateContext() {
      contextBuffer[contextIndex][0] = currentOffsetH;
      contextBuffer[contextIndex][1] = currentOffsetV;
      contextIndex = (contextIndex + 1) % 10;
   }

   private int ticksAiming = 0;
   public void setTicksAiming(int t) { ticksAiming = t; }
   public int getTicksAiming() { return ticksAiming; }

   public boolean shouldLearn() { return false; }
   public void learn() {}

   private void syncTargetNetwork() {}

   public void tickEpsilon() {}

   public void incrementBattles() { history.incrementBattles(); }
   public void incrementBattleStart() { history.incrementBattleNow(); }

   public void checkLogs() {
      if (history.shouldLog()) {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            mc.player.sendMessage(Text.of(history.getLogMessage()), false);
         }
      }
   }

   public void resetTarget() {
      currentOffsetH = 0f;
      currentOffsetV = 0f;
      targetOffsetH = 0f;
      targetOffsetV = 0f;
      contextIndex = 0;
      ticksAiming = 0;
      hasPrev = false;
      velocityHFilter.reset();
      velocityVFilter.reset();
      speedFilter.reset();
      aggressionFilter.reset(0.5f);
      jitterFilter.reset();
      for (int i = 0; i < 10; i++) {
         contextBuffer[i][0] = 0f;
         contextBuffer[i][1] = 0f;
      }
   }

   public void resetAll() { resetTarget(); }

   public void saveToFile() {
      history.save();
      if (brainFile != null && agent != null) {
         agent.save(brainFile);
      }
   }

   public void loadFromFile() {
      if (brainFile != null && brainFile.exists()) {
         agent = RLAgent.load(brainFile);
         if (agent == null) {
            agent = new RLAgent(ExpertCapture.getActiveServerId());
         }
      }
   }

   public RLAgent getAgent() { return agent; }
   public void setAgent(RLAgent a) { this.agent = a; }
   public HistoryManager getHistory() { return history; }

   public float getCachedDistance() { return cachedDistance; }
   public float getCachedTargetVelocity() { return cachedTargetVelocity; }

   public int getContextIndex() { return contextIndex; }
   public float[][] getContextBuffer() { return contextBuffer; }

   private static float clamp01(float v) { return v < 0.0f ? 0.0f : Math.min(v, 1.0f); }
}
