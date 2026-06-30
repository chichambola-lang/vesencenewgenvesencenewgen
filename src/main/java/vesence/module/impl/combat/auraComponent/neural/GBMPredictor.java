package vesence.module.impl.combat.auraComponent.neural;

import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Environment(EnvType.CLIENT)
public final class GBMPredictor {
   private static final int NUM_TREES = 40;
   private static final int MAX_DEPTH = 3;
   private static final int NUM_ACTIONS = 9;
   private static final float LEARNING_RATE = 0.1f;
   private static final int MIN_SAMPLES = 20;

   private final int[][] treeFeatureIdx = new int[NUM_TREES][];
   private final float[][] treeThreshold = new float[NUM_TREES][];
   private final int[][][] treeChildren = new int[NUM_TREES][][];
   private final float[][] treeValues = new float[NUM_TREES][];
   private int trainedTrees = 0;
   private volatile boolean trained = false;
   private volatile boolean isTraining = false;

   private final float[] basePrediction = new float[NUM_ACTIONS];

   public GBMPredictor() {
   }

   public float[] predict(float[] features) {
      float[] result = new float[NUM_ACTIONS];
      System.arraycopy(basePrediction, 0, result, 0, NUM_ACTIONS);

      for (int t = 0; t < trainedTrees; t++) {
         int node = 0;
         for (int d = 0; d < MAX_DEPTH; d++) {
            if (treeFeatureIdx[t] == null || node >= treeFeatureIdx[t].length) break;
            int featIdx = treeFeatureIdx[t][node];
            if (featIdx < 0 || featIdx >= features.length) break;
            if (features[featIdx] <= treeThreshold[t][node]) {
               node = treeChildren[t][node][0];
            } else {
               node = treeChildren[t][node][1];
            }
            if (node < 0) break;
         }

         if (t < treeValues.length && node >= 0 && node * NUM_ACTIONS + NUM_ACTIONS <= treeValues[t].length) {
            int base = node * NUM_ACTIONS;
            for (int a = 0; a < NUM_ACTIONS; a++) {
               result[a] += LEARNING_RATE * treeValues[t][base + a];
            }
         }
      }
      return result;
   }

   public int getBestAction(float[] features) {
      float[] pred = predict(features);
      int best = 0;
      for (int a = 1; a < NUM_ACTIONS; a++) {
         if (pred[a] > pred[best]) best = a;
      }
      return best;
   }

   public float[] getActionConfidences(float[] features) {
      float[] pred = predict(features);
      float min = Float.MAX_VALUE;
      float max = Float.NEGATIVE_INFINITY;
      for (float v : pred) {
         if (v < min) min = v;
         if (v > max) max = v;
      }
      float range = max - min;
      if (range < 0.001f) range = 1.0f;
      float[] conf = new float[NUM_ACTIONS];
      for (int a = 0; a < NUM_ACTIONS; a++) {
         conf[a] = (pred[a] - min) / range;
      }
      return conf;
   }

    public void train(HistoryManager history) {
       if (isTraining) return;
       isTraining = true;
       try {
       int n = history.size();
       if (n < MIN_SAMPLES) { isTraining = false; return; }

      int samples = Math.min(n, 2000);
      int startIdx = Math.max(0, n - samples);

      float[][] X = new float[samples][];
      float[][] y = new float[samples][];
      for (int i = 0; i < samples; i++) {
         X[i] = history.getStates(startIdx + i);
         int action = history.getAction(startIdx + i);
         float reward = history.getReward(startIdx + i);
         y[i] = new float[NUM_ACTIONS];
         if (action >= 0 && action < NUM_ACTIONS) {
            y[i][action] = reward;
         }
      }

      for (int a = 0; a < NUM_ACTIONS; a++) {
         float sum = 0f;
         for (int i = 0; i < samples; i++) sum += y[i][a];
         basePrediction[a] = sum / samples;
      }

      float[][] currentPred = new float[samples][NUM_ACTIONS];
      for (int i = 0; i < samples; i++) {
         System.arraycopy(basePrediction, 0, currentPred[i], 0, NUM_ACTIONS);
      }

      trainedTrees = 0;
      for (int t = 0; t < NUM_TREES; t++) {
         float[][] residuals = new float[samples][NUM_ACTIONS];
         for (int i = 0; i < samples; i++) {
            for (int a = 0; a < NUM_ACTIONS; a++) {
               residuals[i][a] = y[i][a] - currentPred[i][a];
            }
         }

         int[] features = new int[samples];
         for (int i = 0; i < samples; i++) features[i] = i;

         TreeNode tree = buildTree(X, residuals, features, 0, MAX_DEPTH);
         if (tree == null) break;

         encodeTree(t, tree);

         for (int i = 0; i < samples; i++) {
            float[] leafVal = predictTree(tree, X[i]);
            for (int a = 0; a < NUM_ACTIONS; a++) {
               currentPred[i][a] += LEARNING_RATE * leafVal[a];
            }
         }

         trainedTrees = t + 1;
      }

       trained = trainedTrees > 0;
       } finally {
          isTraining = false;
       }
    }

   private TreeNode buildTree(float[][] X, float[][] y, int[] sampleIdx, int depth, int maxDepth) {
      if (depth >= maxDepth || sampleIdx.length < 5) {
         return createLeaf(y, sampleIdx);
      }

      int bestFeature = -1;
      float bestThreshold = 0f;
      float bestGain = 0f;
      int numFeatures = X[0].length;

      for (int f = 0; f < numFeatures; f++) {
         float minVal = Float.MAX_VALUE;
         float maxVal = Float.NEGATIVE_INFINITY;
         for (int idx : sampleIdx) {
            float v = X[idx][f];
            if (v < minVal) minVal = v;
            if (v > maxVal) maxVal = v;
         }
         if (maxVal - minVal < 0.001f) continue;

         int numThresholds = Math.min(10, sampleIdx.length);
         for (int t = 0; t < numThresholds; t++) {
            float threshold = minVal + (maxVal - minVal) * (t + 1) / (numThresholds + 1);
            float gain = computeGain(y, sampleIdx, X, f, threshold);
            if (gain > bestGain) {
               bestGain = gain;
               bestFeature = f;
               bestThreshold = threshold;
            }
         }
      }

      if (bestFeature < 0 || bestGain < 0.001f) {
         return createLeaf(y, sampleIdx);
      }

      int leftCount = 0, rightCount = 0;
      for (int idx : sampleIdx) {
         if (X[idx][bestFeature] <= bestThreshold) leftCount++;
         else rightCount++;
      }

      int[] leftIdx = new int[leftCount];
      int[] rightIdx = new int[rightCount];
      int li = 0, ri = 0;
      for (int idx : sampleIdx) {
         if (X[idx][bestFeature] <= bestThreshold) leftIdx[li++] = idx;
         else rightIdx[ri++] = idx;
      }

      TreeNode left = buildTree(X, y, leftIdx, depth + 1, maxDepth);
      TreeNode right = buildTree(X, y, rightIdx, depth + 1, maxDepth);

      if (left == null && right == null) return createLeaf(y, sampleIdx);

      return new TreeNode(bestFeature, bestThreshold, left, right);
   }

   private float computeGain(float[][] y, int[] idx, float[][] X, int feat, float threshold) {
      float[] leftSum = new float[NUM_ACTIONS];
      float[] rightSum = new float[NUM_ACTIONS];
      float[] totalSum = new float[NUM_ACTIONS];
      int leftN = 0, rightN = 0;

      for (int i : idx) {
         if (X[i][feat] <= threshold) {
            leftN++;
            for (int a = 0; a < NUM_ACTIONS; a++) leftSum[a] += y[i][a];
         } else {
            rightN++;
            for (int a = 0; a < NUM_ACTIONS; a++) rightSum[a] += y[i][a];
         }
         for (int a = 0; a < NUM_ACTIONS; a++) totalSum[a] += y[i][a];
      }

      if (leftN < 3 || rightN < 3) return 0f;

      float totalVar = 0f, leftVar = 0f, rightVar = 0f;
      int totalN = leftN + rightN;
      for (int a = 0; a < NUM_ACTIONS; a++) {
         float totalMean = totalSum[a] / totalN;
         float leftMean = leftSum[a] / leftN;
         float rightMean = rightSum[a] / rightN;
         totalVar += totalMean * totalMean * totalN;
         leftVar += leftMean * leftMean * leftN;
         rightVar += rightMean * rightMean * rightN;
      }

      return leftVar + rightVar - totalVar;
   }

   private TreeNode createLeaf(float[][] y, int[] sampleIdx) {
      float[] values = new float[NUM_ACTIONS];
      int n = sampleIdx.length;
      for (int i : sampleIdx) {
         for (int a = 0; a < NUM_ACTIONS; a++) values[a] += y[i][a];
      }
      for (int a = 0; a < NUM_ACTIONS; a++) values[a] /= Math.max(n, 1);
      TreeNode leaf = new TreeNode(-1, 0f, null, null);
      leaf.values = values;
      return leaf;
   }

   private float[] predictTree(TreeNode node, float[] x) {
      if (node == null) return new float[NUM_ACTIONS];
      if (node.featureIdx < 0) return node.values != null ? node.values : new float[NUM_ACTIONS];
      if (x[node.featureIdx] <= node.threshold) return predictTree(node.left, x);
      return predictTree(node.right, x);
   }

   private void encodeTree(int treeIdx, TreeNode root) {
      int nodeCount = countNodes(root);
      treeFeatureIdx[treeIdx] = new int[nodeCount];
      treeThreshold[treeIdx] = new float[nodeCount];
      treeChildren[treeIdx] = new int[nodeCount][2];
      treeValues[treeIdx] = new float[nodeCount * NUM_ACTIONS];

      for (int i = 0; i < nodeCount; i++) {
         treeFeatureIdx[treeIdx][i] = -1;
         treeChildren[treeIdx][i][0] = -1;
         treeChildren[treeIdx][i][1] = -1;
      }

      encodeNode(treeIdx, root, 0, new int[]{0});
   }

   private void encodeNode(int treeIdx, TreeNode node, int depth, int[] counter) {
      if (node == null) return;
      int idx = counter[0]++;
      if (idx >= treeFeatureIdx[treeIdx].length) return;

      if (node.featureIdx < 0) {
         treeFeatureIdx[treeIdx][idx] = -1;
         if (node.values != null) {
            System.arraycopy(node.values, 0, treeValues[treeIdx], idx * NUM_ACTIONS, NUM_ACTIONS);
         }
         return;
      }

      treeFeatureIdx[treeIdx][idx] = node.featureIdx;
      treeThreshold[treeIdx][idx] = node.threshold;

      int leftIdx = counter[0];
      treeChildren[treeIdx][idx][0] = leftIdx;
      encodeNode(treeIdx, node.left, depth + 1, counter);

      int rightIdx = counter[0];
      treeChildren[treeIdx][idx][1] = rightIdx;
      encodeNode(treeIdx, node.right, depth + 1, counter);
   }

   private int countNodes(TreeNode node) {
      if (node == null) return 0;
      if (node.featureIdx < 0) return 1;
      return 1 + countNodes(node.left) + countNodes(node.right);
   }

    public boolean isTrained() {
       return trained;
    }

    public boolean isTraining() {
       return isTraining;
    }

   public void saveToFile(File file) {
      if (file == null) return;
      try {
         JsonObject root = new JsonObject();
         root.addProperty("trainedTrees", trainedTrees);

         JsonArray baseArr = new JsonArray();
         for (float v : basePrediction) baseArr.add(v);
         root.add("basePrediction", baseArr);

         JsonArray treesArr = new JsonArray();
         for (int t = 0; t < trainedTrees; t++) {
            JsonObject treeObj = new JsonObject();

            JsonArray featArr = new JsonArray();
            if (treeFeatureIdx[t] != null) {
               for (int v : treeFeatureIdx[t]) featArr.add(v);
            }
            treeObj.add("features", featArr);

            JsonArray threshArr = new JsonArray();
            if (treeThreshold[t] != null) {
               for (float v : treeThreshold[t]) threshArr.add(v);
            }
            treeObj.add("thresholds", threshArr);

            JsonArray valArr = new JsonArray();
            if (treeValues[t] != null) {
               for (float v : treeValues[t]) valArr.add(v);
            }
            treeObj.add("values", valArr);

            JsonArray childrenArr = new JsonArray();
            if (treeChildren[t] != null) {
               for (int i = 0; i < treeChildren[t].length; i++) {
                  JsonArray pair = new JsonArray();
                  pair.add(treeChildren[t][i][0]);
                  pair.add(treeChildren[t][i][1]);
                  childrenArr.add(pair);
               }
            }
            treeObj.add("children", childrenArr);

            treesArr.add(treeObj);
         }
         root.add("trees", treesArr);

         file.getParentFile().mkdirs();
         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         try (FileWriter writer = new FileWriter(file)) {
            writer.write(gson.toJson(root));
         }
      } catch (IOException ignored) {
      }
   }

   public void loadFromFile(File file) {
      if (file == null || !file.exists()) return;
      try (FileReader reader = new FileReader(file)) {
         JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

         if (root.has("trainedTrees")) trainedTrees = root.get("trainedTrees").getAsInt();
         if (root.has("basePrediction")) {
            JsonArray arr = root.getAsJsonArray("basePrediction");
            for (int i = 0; i < Math.min(arr.size(), NUM_ACTIONS); i++) {
               basePrediction[i] = arr.get(i).getAsFloat();
            }
         }

         if (root.has("trees")) {
            JsonArray treesArr = root.getAsJsonArray("trees");
            for (int t = 0; t < Math.min(treesArr.size(), NUM_TREES); t++) {
               JsonObject treeObj = treesArr.get(t).getAsJsonObject();

               if (treeObj.has("features")) {
                  JsonArray arr = treeObj.getAsJsonArray("features");
                  treeFeatureIdx[t] = new int[arr.size()];
                  for (int i = 0; i < arr.size(); i++) treeFeatureIdx[t][i] = arr.get(i).getAsInt();
               }
               if (treeObj.has("thresholds")) {
                  JsonArray arr = treeObj.getAsJsonArray("thresholds");
                  treeThreshold[t] = new float[arr.size()];
                  for (int i = 0; i < arr.size(); i++) treeThreshold[t][i] = arr.get(i).getAsFloat();
               }
               if (treeObj.has("values")) {
                  JsonArray arr = treeObj.getAsJsonArray("values");
                  treeValues[t] = new float[arr.size()];
                  for (int i = 0; i < arr.size(); i++) treeValues[t][i] = arr.get(i).getAsFloat();
               }

               int nodeCount = treeFeatureIdx[t] != null ? treeFeatureIdx[t].length : 0;
               treeChildren[t] = new int[nodeCount][2];

               if (treeObj.has("children")) {
                  JsonArray childrenArr = treeObj.getAsJsonArray("children");
                  for (int i = 0; i < Math.min(childrenArr.size(), nodeCount); i++) {
                     JsonArray pair = childrenArr.get(i).getAsJsonArray();
                     treeChildren[t][i][0] = pair.get(0).getAsInt();
                     treeChildren[t][i][1] = pair.get(1).getAsInt();
                  }
               } else {
                  for (int i = 0; i < nodeCount; i++) {
                     if (treeFeatureIdx[t] != null && treeFeatureIdx[t][i] < 0) {
                        treeChildren[t][i][0] = -1;
                        treeChildren[t][i][1] = -1;
                     } else {
                        treeChildren[t][i][0] = i * 2 + 1;
                        treeChildren[t][i][1] = i * 2 + 2;
                     }
                  }
               }
            }
         }

         trained = trainedTrees > 0;
      } catch (IOException | IllegalStateException ignored) {
      }
   }

   private static class TreeNode {
      int featureIdx;
      float threshold;
      TreeNode left, right;
      float[] values;

      TreeNode(int featureIdx, float threshold, TreeNode left, TreeNode right) {
         this.featureIdx = featureIdx;
         this.threshold = threshold;
         this.left = left;
         this.right = right;
      }
   }
}
