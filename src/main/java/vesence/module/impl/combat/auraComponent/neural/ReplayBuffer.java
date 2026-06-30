package vesence.module.impl.combat.auraComponent.neural;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class ReplayBuffer {
   public static final int CAPACITY = 10000;

   private final float[][] states = new float[CAPACITY][];
   private final float[][] nextStates = new float[CAPACITY][];
   private final int[] actions = new int[CAPACITY];
   private final float[] rewards = new float[CAPACITY];
   private final boolean[] dones = new boolean[CAPACITY];

   private int head = 0;
   private int size = 0;
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

   public void add(float[] s, int a, float r, float[] sNext, boolean done) {
      lock.writeLock().lock();
      try {
         if (states[head] == null || states[head].length != s.length) {
            states[head] = new float[s.length];
            nextStates[head] = new float[sNext.length];
         }
         System.arraycopy(s, 0, states[head], 0, s.length);
         System.arraycopy(sNext, 0, nextStates[head], 0, sNext.length);
         actions[head] = a;
         rewards[head] = r;
         dones[head] = done;
         head = (head + 1) % CAPACITY;
         if (size < CAPACITY) size++;
      } finally {
         lock.writeLock().unlock();
      }
   }

   public int size() {
      lock.readLock().lock();
      try { return size; } finally { lock.readLock().unlock(); }
   }

   public ReplaySample[] sample(int batchSize) {
      lock.readLock().lock();
      try {
         int n = Math.min(batchSize, size);
         ReplaySample[] batch = new ReplaySample[n];
         ThreadLocalRandom rng = ThreadLocalRandom.current();
         for (int i = 0; i < n; i++) {
            int idx = rng.nextInt(size);
            batch[i] = new ReplaySample(
               states[idx], actions[idx], rewards[idx], nextStates[idx], dones[idx]
            );
         }
         return batch;
      } finally {
         lock.readLock().unlock();
      }
   }

   public void clear() {
      lock.writeLock().lock();
      try {
         head = 0;
         size = 0;
      } finally {
         lock.writeLock().unlock();
      }
   }

   public static final class ReplaySample {
      public final float[] state;
      public final int action;
      public final float reward;
      public final float[] nextState;
      public final boolean done;

      public ReplaySample(float[] state, int action, float reward, float[] nextState, boolean done) {
         this.state = state;
         this.action = action;
         this.reward = reward;
         this.nextState = nextState;
         this.done = done;
      }
   }
}
