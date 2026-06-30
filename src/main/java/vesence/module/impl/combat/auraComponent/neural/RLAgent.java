package vesence.module.impl.combat.auraComponent.neural;

import java.io.*;

public final class RLAgent implements Serializable {
   private static final long serialVersionUID = 1L;

   public static final int STATE_SIZE = 43;
   public static final float EPSILON_START = 0.3f;
   public static final float EPSILON_MIN = 0.05f;
   public static final float EPSILON_DECAY = 0.9995f;
   public static final float GAMMA = 0.95f;
   public static final float LR = 0.001f;
   public static final int BATCH_SIZE = 32;
   public static final int TRAIN_EVERY_N_STEPS = 4;

   private transient QNetwork qnet;
   private final ReplayBuffer buffer = new ReplayBuffer();
   private float epsilon = EPSILON_START;
   private int totalSteps = 0;
   private int trainCounter = 0;
   private static final int LOG_EVERY = 100;
   private final String serverId;

   public RLAgent(String serverId) {
      this.serverId = serverId;
      this.qnet = new QNetwork(STATE_SIZE);
   }

   public int selectAction(float[] state) {
      if (state == null) return QNetwork.NUM_ACTIONS / 2;
      return qnet.selectActionEpsilonGreedy(state, epsilon);
   }

   public int selectGreedy(float[] state) {
      return qnet.selectGreedy(state);
   }

   public void observe(float[] s, int a, float r, float[] sNext, boolean done) {
      if (s == null || sNext == null) return;
      buffer.add(s, a, r, sNext, done);
      totalSteps++;
      epsilon = Math.max(EPSILON_MIN, epsilon * EPSILON_DECAY);

      if (buffer.size() >= BATCH_SIZE && totalSteps % TRAIN_EVERY_N_STEPS == 0) {
         trainStep();
      }
   }

   public void trainStep() {
      ReplayBuffer.ReplaySample[] batch = buffer.sample(BATCH_SIZE);
      qnet.update(batch, GAMMA, LR);
      trainCounter++;
      if (trainCounter % LOG_EVERY == 0) {
         double avgReward = 0.0;
         for (ReplayBuffer.ReplaySample s : batch) {
            avgReward += s.reward;
         }
         avgReward /= batch.length;
         System.out.println("[AI-RL] steps=" + totalSteps
               + " epsilon=" + String.format("%.4f", epsilon)
               + " buffer=" + buffer.size()
               + " avgReward=" + String.format("%.4f", avgReward));
      }
   }

   public void warmupFromHistory(HistoryManager history) {
      if (history == null) return;
      int n = history.size();
      if (n < 2) return;

      float[] prevState = null;
      int prevAction = 0;
      float prevReward = 0f;

      for (int i = 0; i < n; i++) {
         float[] state = history.getStates(i);
         int action = history.getAction(i);
         float reward = history.getReward(i);

         if (prevState != null && state != null) {
            buffer.add(prevState, prevAction, prevReward, state, false);
         }

         prevState = state;
         prevAction = action;
         prevReward = reward;
      }

      for (int warm = 0; warm < 50 && buffer.size() >= BATCH_SIZE; warm++) {
         trainStep();
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

   public static RLAgent load(File file) {
      if (file == null || !file.exists()) return null;
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
         Object obj = ois.readObject();
         if (obj instanceof RLAgent) {
            RLAgent agent = (RLAgent) obj;
            if (agent.qnet == null) agent.qnet = new QNetwork(STATE_SIZE);
            return agent;
         }
      } catch (IOException | ClassNotFoundException ignored) {
      }
      return null;
   }

   public float getEpsilon() { return epsilon; }
   public int getBufferSize() { return buffer.size(); }
   public int getTotalSteps() { return totalSteps; }
   public String getServerId() { return serverId; }
   public QNetwork getQNetwork() { return qnet; }
}
