package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class ExpertCapture {
   public static final int MAGIC = 0x45434850;
   public static final int VERSION = 3;
   public static final int STATE_SIZE = 43;
   public static final int SAMPLE_BYTES = STATE_SIZE * 4 + 4 + 4 + 4 + 4 + 1 + 4 + 4 + 4 + 4;
   public static final int HEADER_BYTES = 4 + 4 + 4;
   public static final int MIN_SAMPLES = 200;

   private static final int CONTEXT_WINDOW = 5;
   private static final float[] rawYawWindow = new float[CONTEXT_WINDOW];
   private static final float[] rawPitchWindow = new float[CONTEXT_WINDOW];
   private static int rawWindowIndex = 0;
   private static int rawWindowFilled = 0;

   private static File activeFile;
   private static FileChannel channel;
   private static boolean recording = false;
   private static int recordedCount = 0;
   private static String activeServerId = "default";
   private static final Object LOCK = new Object();

   private static final int RING_SIZE = 8;
   private static final float[] RING_DY = new float[RING_SIZE];
   private static final float[] RING_DP = new float[RING_SIZE];
   private static final float[] RING_RAW_YAW = new float[RING_SIZE];
   private static final float[] RING_RAW_PITCH = new float[RING_SIZE];
   private static int ringIndex = 0;
   private static int ringFilled = 0;
   private static float lastFreeLookCaptured = 0f;
   private static volatile boolean debugMode = false;
   private static long lastDebugLogMs = 0L;

   private ExpertCapture() {
   }

   public static File getBaseDir() {
      String os = System.getProperty("os.name").toLowerCase();
      File dir;
      if (os.contains("win")) {
         dir = new File("C:\\Vesence\\Vesence");
      } else {
         dir = new File(System.getProperty("user.home"), ".vesence");
      }
      dir.mkdirs();
      return dir;
   }

   public static String currentServerId() {
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null) return "default";
         if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
            return sanitize(mc.getCurrentServerEntry().address);
         }
         if (mc.player != null && mc.player.networkHandler != null
               && mc.player.networkHandler.getConnection() != null) {
            SocketAddress addr = mc.player.networkHandler.getConnection().getAddress();
            if (addr != null) {
               return sanitize(addr.toString());
            }
         }
      } catch (Throwable ignored) {
      }
      return "singleplayer";
   }

   private static String sanitize(String raw) {
      if (raw == null || raw.isEmpty()) return "default";
      String s = raw.toLowerCase().replaceAll("[^a-z0-9._-]", "_");
      if (s.length() > 80) s = s.substring(0, 80);
      return s;
   }

   public static File captureFile(String serverId) {
      return new File(getBaseDir(), "expert_capture_" + sanitize(serverId) + ".bin");
   }

   public static File brainFile(String serverId) {
      return new File(getBaseDir(), "aim_brain_bc_" + sanitize(serverId) + ".json");
   }

   public static boolean isRecording() {
      synchronized (LOCK) {
         return recording;
      }
   }

   public static void setDebug(boolean on) {
      debugMode = on;
   }

   public static boolean isDebug() {
      return debugMode;
   }

   public static void pushRing(float dy, float dp, float rawYaw, float rawPitch) {
      synchronized (LOCK) {
         RING_DY[ringIndex] = dy;
         RING_DP[ringIndex] = dp;
         RING_RAW_YAW[ringIndex] = rawYaw;
         RING_RAW_PITCH[ringIndex] = rawPitch;
         ringIndex = (ringIndex + 1) % RING_SIZE;
         if (ringFilled < RING_SIZE) ringFilled++;
      }
   }

   public static float ringMeanDy() {
      synchronized (LOCK) {
         if (ringFilled == 0) return 0f;
         float s = 0f;
         for (int i = 0; i < ringFilled; i++) s += RING_DY[i];
         return s / ringFilled;
      }
   }

   public static float ringMeanDp() {
      synchronized (LOCK) {
         if (ringFilled == 0) return 0f;
         float s = 0f;
         for (int i = 0; i < ringFilled; i++) s += RING_DP[i];
         return s / ringFilled;
      }
   }

   public static float ringMeanRawYaw() {
      synchronized (LOCK) {
         if (ringFilled == 0) return 0f;
         float s = 0f;
         for (int i = 0; i < ringFilled; i++) s += RING_RAW_YAW[i];
         return s / ringFilled;
      }
   }

   public static float ringMeanRawPitch() {
      synchronized (LOCK) {
         if (ringFilled == 0) return 0f;
         float s = 0f;
         for (int i = 0; i < ringFilled; i++) s += RING_RAW_PITCH[i];
         return s / ringFilled;
      }
   }

   public static boolean ringHasData() {
      return ringFilled > 0;
   }

   public static void pushRawWindow(float rawYaw, float rawPitch) {
      rawYawWindow[rawWindowIndex] = rawYaw;
      rawPitchWindow[rawWindowIndex] = rawPitch;
      rawWindowIndex = (rawWindowIndex + 1) % CONTEXT_WINDOW;
      if (rawWindowFilled < CONTEXT_WINDOW) rawWindowFilled++;
   }

   public static float[] getWindowContext() {
      float[] ctx = new float[CONTEXT_WINDOW * 2];
      for (int i = 0; i < CONTEXT_WINDOW; i++) {
         int idx = (rawWindowIndex - 1 - i + CONTEXT_WINDOW * 2) % CONTEXT_WINDOW;
         ctx[i] = rawWindowFilled > i ? rawYawWindow[idx] : 0f;
         ctx[CONTEXT_WINDOW + i] = rawWindowFilled > i ? rawPitchWindow[idx] : 0f;
      }
      return ctx;
   }

   public static void resetRawWindow() {
      rawWindowIndex = 0;
      rawWindowFilled = 0;
      java.util.Arrays.fill(rawYawWindow, 0f);
      java.util.Arrays.fill(rawPitchWindow, 0f);
   }

   public static void markFreeLookHijack() {
      lastFreeLookCaptured = 1f;
   }

   public static float consumeFreeLookHijack() {
      float v = lastFreeLookCaptured;
      lastFreeLookCaptured = 0f;
      return v;
   }

   public static void maybeLogDebug(String line) {
      if (!debugMode) return;
      long now = System.currentTimeMillis();
      if (now - lastDebugLogMs < 1500L) return;
      lastDebugLogMs = now;
      try {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc.player != null) {
            mc.player.sendMessage(net.minecraft.text.Text.of("§7[§bAim§7] §f" + line), false);
         }
      } catch (Throwable ignored) {
      }
   }

   public static int count() {
      synchronized (LOCK) {
         if (recording) return recordedCount;
         return countOnDisk(activeFile);
      }
   }

   public static int countForServer(String serverId) {
      return countOnDisk(captureFile(serverId));
   }

   public static String activeServer() {
      synchronized (LOCK) {
         return activeServerId;
      }
   }

   public static boolean start(String serverId) {
      synchronized (LOCK) {
         if (recording) return false;
         activeServerId = sanitize(serverId == null ? currentServerId() : serverId);
         activeFile = captureFile(activeServerId);
         try {
            activeFile.getParentFile().mkdirs();
            channel = new RandomAccessFile(activeFile, "rw").getChannel();
            boolean headerOk = false;
            if (channel.size() >= HEADER_BYTES) {
               ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
               channel.position(0);
               if (channel.read(header) == HEADER_BYTES) {
                  header.flip();
                  int magic = header.getInt();
                  int version = header.getInt();
                  if (magic == MAGIC && version == VERSION) {
                     headerOk = true;
                  }
               }
            }
            if (!headerOk) {
               channel.position(0);
               channel.truncate(0);
               ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
               header.putInt(MAGIC);
               header.putInt(VERSION);
               header.putInt(0);
               header.flip();
               channel.write(header, 0);
            }
            channel.position(channel.size());
            recordedCount = countOnDisk(activeFile);
            recording = true;
            resetRawWindow();
            return true;
         } catch (IOException ex) {
            tryClose();
            channel = null;
            activeFile = null;
            return false;
         }
      }
   }

   public static boolean stop() {
      synchronized (LOCK) {
         if (!recording) return false;
         int finalCount = recordedCount;
         File fileToUpdate = activeFile;
         tryClose();
         recording = false;
         recordedCount = 0;
         if (fileToUpdate != null) {
            try (RandomAccessFile raf = new RandomAccessFile(fileToUpdate, "rw")) {
               ByteBuffer h = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
               h.putInt(MAGIC);
               h.putInt(VERSION);
               h.putInt(finalCount);
               h.flip();
               raf.getChannel().write(h, 0);
            } catch (IOException ignored) {
            }
         }
         return finalCount >= 0;
      }
   }

   private static void tryClose() {
      if (channel != null) {
         try {
            channel.force(true);
         } catch (IOException ignored) {
         }
         try {
            channel.close();
         } catch (IOException ignored) {
         }
         channel = null;
      }
   }

    public static void record(float[] state, float dy, float dp, float rawMouseDx, float rawMouseDy, boolean hit, int targetId,
                              float aimLocalX, float aimLocalY, float aimLocalZ) {
      synchronized (LOCK) {
         if (!recording || channel == null || state == null || state.length != STATE_SIZE) return;
         if (recordedCount >= 200_000) return;
         if (Math.abs(dy) > 90.0F || Math.abs(dp) > 60.0F) return;
         try {
            ByteBuffer buf = ByteBuffer.allocate(SAMPLE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < STATE_SIZE; i++) buf.putFloat(state[i]);
            buf.putFloat(dy);
            buf.putFloat(dp);
            buf.putFloat(rawMouseDx);
            buf.putFloat(rawMouseDy);
            buf.put((byte) (hit ? 1 : 0));
            buf.putInt(targetId);
            buf.putFloat(aimLocalX);
            buf.putFloat(aimLocalY);
            buf.putFloat(aimLocalZ);
            buf.flip();
            long pos = channel.position();
            channel.write(buf, pos);
            channel.position(pos + SAMPLE_BYTES);
            recordedCount++;
         } catch (IOException ignored) {
         }
      }
   }

   public static boolean clear(String serverId) {
      synchronized (LOCK) {
         if (recording) return false;
         File f = captureFile(serverId);
         if (f.exists()) {
            return f.delete();
         }
         return true;
      }
   }

   public static boolean clearAll() {
      synchronized (LOCK) {
         if (recording) return false;
         File dir = getBaseDir();
         File[] files = dir.listFiles((d, name) -> name.startsWith("expert_capture_") && name.endsWith(".bin"));
         if (files == null) return true;
         boolean ok = true;
         for (File f : files) {
            if (!f.delete()) ok = false;
         }
         return ok;
      }
   }

   public static int countOnDisk(File file) {
      if (file == null || !file.exists()) return 0;
      long size = file.length();
      if (size < HEADER_BYTES) return 0;
      return (int) ((size - HEADER_BYTES) / SAMPLE_BYTES);
   }

   public static List<Sample> loadAll(File file) {
      List<Sample> out = new ArrayList<>();
      if (file == null || !file.exists()) return out;
      try (RandomAccessFile raf = new RandomAccessFile(file, "r");
           FileChannel ch = raf.getChannel()) {
         long size = ch.size();
         if (size < HEADER_BYTES) return out;
         ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
         ch.position(0);
         if (ch.read(header) != HEADER_BYTES) return out;
         header.flip();
         int magic = header.getInt();
         int version = header.getInt();
         if (magic != MAGIC || version != VERSION) return out;
         long bodySize = size - HEADER_BYTES;
         int realCount = (int) (bodySize / SAMPLE_BYTES);
         int leftover = (int) (bodySize - (long) realCount * SAMPLE_BYTES);
         if (realCount <= 0 || leftover != 0) return out;
         ByteBuffer body = ByteBuffer.allocate((int) bodySize).order(ByteOrder.LITTLE_ENDIAN);
         ch.position(HEADER_BYTES);
         ch.read(body);
         body.flip();
           for (int i = 0; i < realCount; i++) {
              float[] state = new float[STATE_SIZE];
              for (int j = 0; j < STATE_SIZE; j++) state[j] = body.getFloat();
              float dy = body.getFloat();
              float dp = body.getFloat();
              float rawMouseDx = body.getFloat();
              float rawMouseDy = body.getFloat();
              byte hb = body.get();
              int tid = body.getInt();
              float aimX = body.getFloat();
              float aimY = body.getFloat();
              float aimZ = body.getFloat();
              out.add(new Sample(state, dy, dp, rawMouseDx, rawMouseDy, hb != 0, tid, aimX, aimY, aimZ));
           }
      } catch (IOException ignored) {
      }
      return out;
   }

   public static float[][] loadStatesForAugmentation(File file, int cap) {
      List<Sample> samples = loadAll(file);
      int n = Math.min(samples.size(), cap);
      float[][] arr = new float[n][];
      for (int i = 0; i < n; i++) arr[i] = samples.get(i).state;
      return arr;
   }

   public static String status() {
      synchronized (LOCK) {
         String sid = activeServerId;
         int rec = recordedCount;
         boolean r = recording;
         int disk = countOnDisk(activeFile);
         return "Сервер: " + sid + " | Захват: " + (r ? "АКТИВЕН" : "выключен")
               + " | Сэмплов на диске: " + disk
               + (r ? (" | В буфере: " + rec) : "");
      }
   }

   public static final class Sample {
      public final float[] state;
      public final float dy;
      public final float dp;
      public final float rawMouseDx;
      public final float rawMouseDy;
      public final boolean hit;
      public final int targetId;
      public final float aimLocalX;
      public final float aimLocalY;
      public final float aimLocalZ;

      public Sample(float[] state, float dy, float dp, float rawMouseDx, float rawMouseDy, boolean hit, int targetId,
                    float aimLocalX, float aimLocalY, float aimLocalZ) {
         this.state = state;
         this.dy = dy;
         this.dp = dp;
         this.rawMouseDx = rawMouseDx;
         this.rawMouseDy = rawMouseDy;
         this.hit = hit;
         this.targetId = targetId;
         this.aimLocalX = aimLocalX;
         this.aimLocalY = aimLocalY;
         this.aimLocalZ = aimLocalZ;
      }
   }

   public static final class ServerInfo {
      public final String id;
      public final int samples;
      public final boolean hasBc;
      public final long lastModified;
      public final long fileSize;
      public final boolean isCurrent;
      public final boolean isActive;

      public ServerInfo(String id, int samples, boolean hasBc, long lastModified, long fileSize,
                        boolean isCurrent, boolean isActive) {
         this.id = id;
         this.samples = samples;
         this.hasBc = hasBc;
         this.lastModified = lastModified;
         this.fileSize = fileSize;
         this.isCurrent = isCurrent;
         this.isActive = isActive;
      }
   }

   public static List<ServerInfo> listServers() {
      List<ServerInfo> out = new ArrayList<>();
      File dir = getBaseDir();
      File[] files = dir.listFiles((d, name) -> name.startsWith("expert_capture_") && name.endsWith(".bin"));
      String currentId = currentServerId();
      String activeId;
      synchronized (LOCK) {
         activeId = activeServerId;
      }
      if (files != null) {
         for (File f : files) {
            String name = f.getName();
            String id = name.substring("expert_capture_".length(), name.length() - ".bin".length());
            int count = countOnDisk(f);
            File bc = brainFile(id);
            boolean hasBc = bc.exists();
            out.add(new ServerInfo(id, count, hasBc, f.lastModified(), f.length(),
               id.equals(currentId), id.equals(activeId)));
         }
      }
      out.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));
      return out;
   }

   public static ServerInfo getServerInfo(String serverId) {
      File f = captureFile(serverId);
      if (!f.exists()) return null;
      int count = countOnDisk(f);
      File bc = brainFile(serverId);
      String currentId = currentServerId();
      String activeId;
      synchronized (LOCK) {
         activeId = activeServerId;
      }
      return new ServerInfo(serverId, count, bc.exists(), f.lastModified(), f.length(),
         serverId.equals(currentId), serverId.equals(activeId));
   }

   public static String getActiveServerId() {
      synchronized (LOCK) {
         return activeServerId;
      }
   }

   public static void setActiveServerId(String id) {
      synchronized (LOCK) {
         activeServerId = sanitize(id == null || id.isEmpty() ? currentServerId() : id);
      }
   }

   public static String resolveServerId(String arg) {
      if (arg == null || arg.isEmpty()) {
         return getActiveServerId();
      }
      return sanitize(arg);
   }
}
