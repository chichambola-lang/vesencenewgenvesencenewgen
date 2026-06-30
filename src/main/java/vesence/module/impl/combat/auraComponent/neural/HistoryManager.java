package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Environment(EnvType.CLIENT)
public final class HistoryManager {
   private static final int MAX_TRANSITIONS = 10000;
   private static final int STATE_SIZE = 33;
   private static final int RECORD_SIZE = 4 + 1 + 4 + 8 + (STATE_SIZE * 4);
   private static final byte[] MAGIC = {'N', 'B', 'R', 'S'};
   private static final int VERSION = 1;
   private static final int HEADER_SIZE = 4 + 4 + 4 + 4 + 4 + 4;

   private float[][] states = new float[MAX_TRANSITIONS][];
   private int[] actions = new int[MAX_TRANSITIONS];
   private float[] rewards = new float[MAX_TRANSITIONS];
   private long[] timestamps = new long[MAX_TRANSITIONS];
   private boolean[] hits = new boolean[MAX_TRANSITIONS];

   private int count = 0;
   private int totalBattles = 0;
   private int totalHits = 0;
   private int intAttacks = 0;

   private int ticksSinceLastSave = 0;
   private static final int SAVE_INTERVAL = 200;
   private int dirtyFrom = 0;

   private long lastLogTime = System.currentTimeMillis();
   private static final long LOG_INTERVAL = 5 * 60 * 1000L;

   private File historyFile;
   private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

   public HistoryManager() {
   }

   public void setFile(File file) {
      this.historyFile = file;
   }

   public void load() {
      if (historyFile == null || !historyFile.exists()) return;
      lock.writeLock().lock();
      try (FileInputStream fis = new FileInputStream(historyFile)) {
         byte[] header = new byte[HEADER_SIZE];
         if (fis.read(header) < HEADER_SIZE) return;
         ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

         byte[] magic = new byte[4];
         hb.get(magic);
         if (magic[0] != MAGIC[0] || magic[1] != MAGIC[1] || magic[2] != MAGIC[2] || magic[3] != MAGIC[3]) return;
         int version = hb.getInt();
         if (version != VERSION) return;
         totalBattles = hb.getInt();
         totalHits = hb.getInt();
         intAttacks = hb.getInt();
         int fileCount = hb.getInt();

         int toRead = Math.min(fileCount, MAX_TRANSITIONS);
         byte[] recordBuf = new byte[RECORD_SIZE];
         count = 0;
         for (int i = 0; i < toRead; i++) {
            if (fis.read(recordBuf) < RECORD_SIZE) break;
            ByteBuffer rb = ByteBuffer.wrap(recordBuf).order(ByteOrder.LITTLE_ENDIAN);
            int action = rb.getInt();
            byte hitByte = rb.get();
            float reward = rb.getFloat();
            long ts = rb.getLong();
            float[] state = new float[STATE_SIZE];
            for (int s = 0; s < STATE_SIZE; s++) state[s] = rb.getFloat();

            actions[count] = action;
            hits[count] = hitByte != 0;
            rewards[count] = reward;
            timestamps[count] = ts;
            states[count] = state;
            count++;
         }
         dirtyFrom = count;
      } catch (IOException ignored) {
      } finally {
         lock.writeLock().unlock();
      }
   }

   public void save() {
      if (historyFile == null) return;
      lock.readLock().lock();
      try {
         historyFile.getParentFile().mkdirs();
         int totalRecords = count;
         int headerSize = HEADER_SIZE;
         int fileSize = headerSize + totalRecords * RECORD_SIZE;

         byte[] data = new byte[fileSize];
         ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

         buf.put(MAGIC);
         buf.putInt(VERSION);
         buf.putInt(totalBattles);
         buf.putInt(totalHits);
         buf.putInt(intAttacks);
         buf.putInt(totalRecords);

         for (int i = 0; i < totalRecords; i++) {
            buf.putInt(actions[i]);
            buf.put(hits[i] ? (byte) 1 : (byte) 0);
            buf.putFloat(rewards[i]);
            buf.putLong(timestamps[i]);
            float[] state = states[i];
            for (int s = 0; s < STATE_SIZE; s++) buf.putFloat(state[s]);
         }

         try (FileOutputStream fos = new FileOutputStream(historyFile)) {
            fos.write(data);
         }
         dirtyFrom = totalRecords;
      } catch (IOException ignored) {
      } finally {
         lock.readLock().unlock();
      }
   }

   public void saveIncremental() {
      if (historyFile == null || dirtyFrom >= count) return;
      lock.readLock().lock();
      try {
         if (!historyFile.exists() || dirtyFrom == 0) {
            save();
            return;
         }

         int newRecords = count - dirtyFrom;
         int appendSize = newRecords * RECORD_SIZE;
         byte[] data = new byte[appendSize];
         ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

         for (int i = dirtyFrom; i < count; i++) {
            buf.putInt(actions[i]);
            buf.put(hits[i] ? (byte) 1 : (byte) 0);
            buf.putFloat(rewards[i]);
            buf.putLong(timestamps[i]);
            float[] state = states[i];
            for (int s = 0; s < STATE_SIZE; s++) buf.putFloat(state[s]);
         }

         try (FileOutputStream fos = new FileOutputStream(historyFile, true)) {
            fos.write(data);
         }

         byte[] countBytes = new byte[4];
         ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(count);
         try (RandomAccessFileWrapper raf = new RandomAccessFileWrapper(historyFile, "rw")) {
            raf.seek(20);
            raf.write(countBytes);
         }
         dirtyFrom = count;
      } catch (IOException ignored) {
      } finally {
         lock.readLock().unlock();
      }
   }

   public void addTransition(float[] state, int action, float reward, boolean hit, boolean isAttack) {
      lock.writeLock().lock();
      try {
         if (count < MAX_TRANSITIONS) {
            if (states[count] == null) states[count] = new float[STATE_SIZE];
            System.arraycopy(state, 0, states[count], 0, Math.min(state.length, STATE_SIZE));
            actions[count] = action;
            rewards[count] = reward;
            timestamps[count] = System.currentTimeMillis();
            hits[count] = hit;
            count++;
         } else {
            System.arraycopy(states, 1, states, 0, MAX_TRANSITIONS - 1);
            System.arraycopy(actions, 1, actions, 0, MAX_TRANSITIONS - 1);
            System.arraycopy(rewards, 1, rewards, 0, MAX_TRANSITIONS - 1);
            System.arraycopy(timestamps, 1, timestamps, 0, MAX_TRANSITIONS - 1);
            System.arraycopy(hits, 1, hits, 0, MAX_TRANSITIONS - 1);
            states[MAX_TRANSITIONS - 1] = state.clone();
            actions[MAX_TRANSITIONS - 1] = action;
            rewards[MAX_TRANSITIONS - 1] = reward;
            timestamps[MAX_TRANSITIONS - 1] = System.currentTimeMillis();
            hits[MAX_TRANSITIONS - 1] = hit;
            dirtyFrom = 0;
         }

         if (isAttack) {
            intAttacks++;
            if (hit) totalHits++;
         }

         ticksSinceLastSave++;
         if (ticksSinceLastSave >= SAVE_INTERVAL) {
            ticksSinceLastSave = 0;
            saveIncremental();
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   public void incrementBattles() {
      totalBattles++;
   }

   public void incrementBattleNow() {
      totalBattles++;
   }

   public void clear() {
      lock.writeLock().lock();
      try {
         count = 0;
         totalBattles = 0;
         totalHits = 0;
         intAttacks = 0;
         dirtyFrom = 0;
         for (int i = 0; i < MAX_TRANSITIONS; i++) {
            states[i] = null;
            actions[i] = 0;
            rewards[i] = 0f;
            hits[i] = false;
         }
      } finally {
         lock.writeLock().unlock();
      }
   }

   public float[] getStates(int index) {
      lock.readLock().lock();
      try {
         return index < count ? states[index] : null;
      } finally {
         lock.readLock().unlock();
      }
   }

   public int getAction(int index) {
      lock.readLock().lock();
      try {
         return index < count ? actions[index] : 0;
      } finally {
         lock.readLock().unlock();
      }
   }

   public float getReward(int index) {
      lock.readLock().lock();
      try {
         return index < count ? rewards[index] : 0f;
      } finally {
         lock.readLock().unlock();
      }
   }

   public int size() {
      return count;
   }

   public int getTotalBattles() {
      return totalBattles;
   }

   public int getTotalHits() {
      return totalHits;
   }

   public int getTotalAttacks() {
      return intAttacks;
   }

   public float getAccuracy() {
      return intAttacks > 0 ? (float) totalHits / intAttacks : 0f;
   }

   public boolean shouldLog() {
      long now = System.currentTimeMillis();
      if (now - lastLogTime >= LOG_INTERVAL) {
         lastLogTime = now;
         return true;
      }
      return false;
   }

   public String getLogMessage() {
      float accuracy = getAccuracy() * 100f;
      float readiness = Math.min(100f, (float) count / MAX_TRANSITIONS * 100f);
      return String.format(
         "\u00a77[\u00a7bAI\u00a77] \u00a7fБоёв: \u00a7a%d\u00a7f | Атак: \u00a7a%d\u00a7f | Попаданий: \u00a7a%d\u00a7f | Точность: \u00a7e%.1f%%\u00a7f | Готовность: \u00a7e%.0f%%",
         totalBattles, intAttacks, totalHits, accuracy, readiness
      );
   }

   public String getBestPattern() {
      if (count < 100) return null;

      int[] actionHits = new int[9];
      int[] actionTotal = new int[9];

      lock.readLock().lock();
      try {
         for (int i = 0; i < count; i++) {
            int a = actions[i];
            if (a >= 0 && a < 9) {
               actionTotal[a]++;
               if (hits[i]) actionHits[a]++;
            }
         }
      } finally {
         lock.readLock().unlock();
      }

      String[] actionNames = {"up", "up-right", "right", "down-right", "stay", "down-left", "left", "up-left", "down"};
      int bestAction = 0;
      float bestRate = 0f;
      for (int a = 0; a < 9; a++) {
         if (actionTotal[a] > 10) {
            float rate = (float) actionHits[a] / actionTotal[a];
            if (rate > bestRate) {
               bestRate = rate;
               bestAction = a;
            }
         }
      }

      if (bestRate > 0.1f) {
         return String.format(
            "\u00a77[\u00a7bAI\u00a77] \u00a7fЛучшее действие: \u00a7a%s\u00a7f (%.0f%% из %d попыток)",
            actionNames[bestAction], bestRate * 100f, actionTotal[bestAction]
         );
      }
      return null;
   }

   private static class RandomAccessFileWrapper implements AutoCloseable {
      private final java.io.RandomAccessFile raf;

      RandomAccessFileWrapper(File file, String mode) throws IOException {
         this.raf = new java.io.RandomAccessFile(file, mode);
      }

      void seek(long pos) throws IOException { raf.seek(pos); }
      void write(byte[] data) throws IOException { raf.write(data); }

      @Override
      public void close() throws IOException { raf.close(); }
   }
}
