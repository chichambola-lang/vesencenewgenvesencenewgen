package vesence.module.impl.combat.auraComponent.neural;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
public final class HitboxWaypoints {

   private static final int GRID_RES = 4;
   private static final int MIN_SAMPLES_PER_CELL = 3;
   private static final float MERGE_DIST = 0.35f;
   private static final float MIN_WAYPOINT_DIST = 0.12f;

   private static List<Waypoint> waypoints = new ArrayList<>();
   private static boolean loaded = false;

   public static final class Waypoint {
      public float x, y, z;
      public float frequency;
      public float radius;
      public int hitCount;
      public int totalCount;

      public Waypoint(float x, float y, float z, float frequency, float radius, int hitCount, int totalCount) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.frequency = frequency;
         this.radius = radius;
         this.hitCount = hitCount;
         this.totalCount = totalCount;
      }

      public boolean isAttackZone() {
         return totalCount > 0 && (float) hitCount / totalCount > 0.3f;
      }
   }

   public static final class BezierPath {
      public final float[] p0;
      public final float[] p1;
      public final float[] p2;
      public final float[] p3;
      public final float totalDist;

      public BezierPath(float[] p0, float[] p1, float[] p2, float[] p3) {
         this.p0 = p0;
         this.p1 = p1;
         this.p2 = p2;
         this.p3 = p3;
         this.totalDist = estimateDist(p0, p3);
      }

      public float[] pointAt(float t) {
         float u = 1.0f - t;
         float uu = u * u;
         float uuu = uu * u;
         float tt = t * t;
         float ttt = tt * t;
         return new float[]{
            uuu * p0[0] + 3.0f * uu * t * p1[0] + 3.0f * u * tt * p2[0] + ttt * p3[0],
            uuu * p0[1] + 3.0f * uu * t * p1[1] + 3.0f * u * tt * p2[1] + ttt * p3[1],
            uuu * p0[2] + 3.0f * uu * t * p1[2] + 3.0f * u * tt * p2[2] + ttt * p3[2]
         };
      }

      private static float estimateDist(float[] a, float[] b) {
         float dx = b[0] - a[0];
         float dy = b[1] - a[1];
         float dz = b[2] - a[2];
         return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      }
   }

   public static final class PathState {
      public boolean active = false;
      public BezierPath currentPath = null;
      public float progress = 0.0f;
      public float speed = 0.08f;
      public float[] currentPos = new float[]{0f, 0f, 0f};
      public int currentWaypointIdx = -1;
      public int nextWaypointIdx = -1;
      public float lingerTicks = 0f;
      public float lingerMax = 8f;

      public void reset() {
         active = false;
         currentPath = null;
         progress = 0.0f;
         currentWaypointIdx = -1;
         nextWaypointIdx = -1;
         lingerTicks = 0f;
      }
   }

   public static void load(File dataset) {
      loaded = false;
      waypoints.clear();
      if (dataset == null || !dataset.exists()) return;

      List<ExpertCapture.Sample> samples = ExpertCapture.loadAll(dataset);
      if (samples.size() < 50) return;

      float gridStep = 1.0f / GRID_RES;
      float[][][] gridX = new float[GRID_RES][GRID_RES][GRID_RES];
      float[][][] gridY = new float[GRID_RES][GRID_RES][GRID_RES];
      float[][][] gridZ = new float[GRID_RES][GRID_RES][GRID_RES];
      int[][][] gridCount = new int[GRID_RES][GRID_RES][GRID_RES];
      int[][][] gridHits = new int[GRID_RES][GRID_RES][GRID_RES];

      float boxHalf = 0.3f;
      float boxMin = -boxHalf;
      float boxRange = boxHalf * 2.0f;

      for (ExpertCapture.Sample s : samples) {
         int gx = (int) MathHelper.clamp((s.aimLocalX - boxMin) / boxRange * GRID_RES, 0, GRID_RES - 1);
         int gy = (int) MathHelper.clamp((s.aimLocalY - boxMin) / boxRange * GRID_RES, 0, GRID_RES - 1);
         int gz = (int) MathHelper.clamp((s.aimLocalZ - boxMin) / boxRange * GRID_RES, 0, GRID_RES - 1);
         gridX[gx][gy][gz] += s.aimLocalX;
         gridY[gx][gy][gz] += s.aimLocalY;
         gridZ[gx][gy][gz] += s.aimLocalZ;
         gridCount[gx][gy][gz]++;
         if (s.hit) gridHits[gx][gy][gz]++;
      }

      List<float[]> rawPoints = new ArrayList<>();
      List<Integer> rawHits = new ArrayList<>();
      List<Integer> rawTotals = new ArrayList<>();

      for (int x = 0; x < GRID_RES; x++) {
         for (int y = 0; y < GRID_RES; y++) {
            for (int z = 0; z < GRID_RES; z++) {
               int count = gridCount[x][y][z];
               if (count < MIN_SAMPLES_PER_CELL) continue;
               float px = gridX[x][y][z] / count;
               float py = gridY[x][y][z] / count;
               float pz = gridZ[x][y][z] / count;
               rawPoints.add(new float[]{px, py, pz});
               rawHits.add(gridHits[x][y][z]);
               rawTotals.add(count);
            }
         }
      }

      if (rawPoints.isEmpty()) return;

      List<float[]> merged = new ArrayList<>();
      List<Integer> mergedHits = new ArrayList<>();
      List<Integer> mergedTotals = new ArrayList<>();
      boolean[] used = new boolean[rawPoints.size()];

      for (int i = 0; i < rawPoints.size(); i++) {
         if (used[i]) continue;
         float cx = rawPoints.get(i)[0];
         float cy = rawPoints.get(i)[1];
         float cz = rawPoints.get(i)[2];
         int totalH = rawHits.get(i);
         int totalN = rawTotals.get(i);

         for (int j = i + 1; j < rawPoints.size(); j++) {
            if (used[j]) continue;
            float dx = rawPoints.get(j)[0] - cx;
            float dy = rawPoints.get(j)[1] - cy;
            float dz = rawPoints.get(j)[2] - cz;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < MERGE_DIST) {
               float w = (float) rawTotals.get(j) / (totalN + rawTotals.get(j));
               cx += dx * w;
               cy += dy * w;
               cz += dz * w;
               totalH += rawHits.get(j);
               totalN += rawTotals.get(j);
               used[j] = true;
            }
         }
         merged.add(new float[]{cx, cy, cz});
         mergedHits.add(totalH);
         mergedTotals.add(totalN);
         used[i] = true;
      }

      float maxFreq = 0f;
      for (int count : mergedTotals) maxFreq = Math.max(maxFreq, count);

      for (int i = 0; i < merged.size(); i++) {
         float[] pos = merged.get(i);
         float freq = maxFreq > 0 ? (float) mergedTotals.get(i) / maxFreq : 0f;
         float radius = 0.15f + (1.0f - freq) * 0.15f;
         waypoints.add(new Waypoint(pos[0], pos[1], pos[2], freq, radius, mergedHits.get(i), mergedTotals.get(i)));
      }

      waypoints.sort((a, b) -> Float.compare(b.frequency, a.frequency));
      loaded = true;
   }

   public static boolean isLoaded() {
      return loaded && !waypoints.isEmpty();
   }

   public static int size() {
      return waypoints.size();
   }

   public static Waypoint get(int idx) {
      if (idx < 0 || idx >= waypoints.size()) return null;
      return waypoints.get(idx);
   }

   public static List<Waypoint> getAll() {
      return waypoints;
   }

   private static int lastPickedIdx = -2;
   private static int staleCount = 0;

   public static Waypoint pickWaypoint(float[] currentPos, boolean preferAttack, float contextRandom) {
      if (waypoints.isEmpty()) return null;

      float bestScore = -10f;
      int bestIdx = 0;
      ThreadLocalRandom rng = ThreadLocalRandom.current();

      boolean forceFarthest = staleCount >= 2;

      for (int i = 0; i < waypoints.size(); i++) {
         Waypoint wp = waypoints.get(i);
         float dx = wp.x - currentPos[0];
         float dy = wp.y - currentPos[1];
         float dz = wp.z - currentPos[2];
         float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

         float distScore;
         if (forceFarthest && i != lastPickedIdx) {
            distScore = MathHelper.clamp(dist / 0.85f, 0f, 1f);
         } else {
            distScore = 1.0f - MathHelper.clamp(dist / 0.85f, 0f, 1f);
         }
         float freqScore = wp.frequency;
         float attackBonus = (preferAttack && wp.isAttackZone()) ? 0.35f : 0f;
         float randomNoise = contextRandom * 0.15f;

         float score = distScore * 0.25f + freqScore * 0.35f + attackBonus + randomNoise;

         if (i == lastPickedIdx) {
            score -= 0.8f;
         }

         if (dist < MIN_WAYPOINT_DIST && !forceFarthest) {
            score -= 0.5f;
         }

         if (score > bestScore) {
            bestScore = score;
            bestIdx = i;
         }
      }

      if (bestIdx == lastPickedIdx && waypoints.size() > 1) {
         staleCount++;
      } else {
         staleCount = 0;
      }
      lastPickedIdx = bestIdx;
      return waypoints.get(bestIdx);
   }

   public static BezierPath createPath(float[] from, Waypoint target, float jitter) {
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      float dx = target.x - from[0];
      float dy = target.y - from[1];
      float dz = target.z - from[2];
      float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

      float ctrlRange = Math.max(dist * 0.4f, 0.05f) * jitter;
      float[] p1 = new float[]{
         from[0] + dx * 0.33f + (rng.nextFloat() - 0.5f) * ctrlRange,
         from[1] + dy * 0.33f + (rng.nextFloat() - 0.5f) * ctrlRange,
         from[2] + dz * 0.33f + (rng.nextFloat() - 0.5f) * ctrlRange
      };
      float[] p2 = new float[]{
         from[0] + dx * 0.66f + (rng.nextFloat() - 0.5f) * ctrlRange,
         from[1] + dy * 0.66f + (rng.nextFloat() - 0.5f) * ctrlRange,
         from[2] + dz * 0.66f + (rng.nextFloat() - 0.5f) * ctrlRange
      };

      return new BezierPath(from.clone(), p1, p2, new float[]{target.x, target.y, target.z});
   }

   public static float boostCurve(float t) {
      return (float) (Math.sin(Math.PI * t) * 0.5f + 0.5f);
   }

   public static float easeInOutCubic(float t) {
      return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
   }

    public static void resetAll() {
      loaded = false;
      waypoints.clear();
      lastPickedIdx = -2;
      staleCount = 0;
   }

   private HitboxWaypoints() {
   }
}
