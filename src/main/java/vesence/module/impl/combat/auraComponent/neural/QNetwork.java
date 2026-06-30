package vesence.module.impl.combat.auraComponent.neural;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;

public final class QNetwork implements Serializable {
   private static final long serialVersionUID = 2L;
   public static final int NUM_ACTIONS = 25;

   private transient SmileMLP qnet;
   private transient SmileMLP targetQnet;
   private int updateCounter = 0;
   private static final int TARGET_UPDATE_FREQ = 100;
   private final int stateSize;

   public QNetwork(int stateSize) {
      this.stateSize = stateSize;
      this.qnet = new SmileMLP(stateSize, NUM_ACTIONS);
      this.targetQnet = qnet.deepCopy();
   }

   public int selectActionEpsilonGreedy(float[] state, float epsilon) {
      if (ThreadLocalRandom.current().nextFloat() < epsilon) {
         return ThreadLocalRandom.current().nextInt(NUM_ACTIONS);
      }
      float[] qvalues = qnet.forward(state);
      int best = 0;
      float bestVal = qvalues[0];
      for (int a = 1; a < NUM_ACTIONS; a++) {
         if (qvalues[a] > bestVal) {
            bestVal = qvalues[a];
            best = a;
         }
      }
      return best;
   }

   public int selectGreedy(float[] state) {
      return selectActionEpsilonGreedy(state, 0f);
   }

   public float[] getQValues(float[] state) {
      return qnet.forward(state);
   }

   /**
    * DQN update с целевыми векторами.
    * Для каждого перехода обновляется только Q(s, a), остальные Q(s, *) остаются неизменными.
    */
   public void update(ReplayBuffer.ReplaySample[] batch, float gamma, float lr) {
      if (batch == null || batch.length == 0) return;

      float[][] states = new float[batch.length][stateSize];
      float[][] targets = new float[batch.length][NUM_ACTIONS];

      double totalReward = 0.0;
      double totalMaxQ = 0.0;

      for (int i = 0; i < batch.length; i++) {
         states[i] = batch[i].state;
         float[] qCurrent = qnet.forward(batch[i].state);
         System.arraycopy(qCurrent, 0, targets[i], 0, NUM_ACTIONS);

         float[] qNext = targetQnet.forward(batch[i].nextState);
         float maxQNext = Float.NEGATIVE_INFINITY;
         for (float v : qNext) {
            if (v > maxQNext) maxQNext = v;
         }

         float target = batch[i].reward + (batch[i].done ? 0f : gamma * maxQNext);
         targets[i][batch[i].action] = target;

         totalReward += batch[i].reward;
         totalMaxQ += maxQNext;
      }

      qnet.trainOnBatch(states, targets, 1, lr);

      updateCounter++;
      if (updateCounter >= TARGET_UPDATE_FREQ) {
         targetQnet = qnet.deepCopy();
         updateCounter = 0;
      }
   }

   public void save(File file) {
      if (file == null) return;
      file.getParentFile().mkdirs();
      try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
         oos.writeObject(this);
      } catch (IOException ignored) {
      }
   }

   public static QNetwork load(File file) {
      if (file == null || !file.exists()) return null;
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
         Object obj = ois.readObject();
         if (obj instanceof QNetwork) {
            QNetwork q = (QNetwork) obj;
            if (q.qnet == null) q.qnet = new SmileMLP(q.stateSize, NUM_ACTIONS);
            if (q.targetQnet == null) q.targetQnet = q.qnet.deepCopy();
            return q;
         }
      } catch (IOException | ClassNotFoundException ignored) {
      }
      return null;
   }

   public int getStateSize() { return stateSize; }
}
