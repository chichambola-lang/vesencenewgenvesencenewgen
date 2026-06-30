package vesence.module.impl.combat.auraComponent.neural;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Простая полносвязная MLP для Q-learning.
 * Заменяет использование smile.classification.MLP, который не подходит
 * для многомерной регрессии Q-значений.
 *
 * Архитектура: input -> tanh(28) -> tanh(18) -> linear(output)
 */
public final class SmileMLP implements Serializable {
   private static final long serialVersionUID = 2L;

   private static final int HIDDEN_1 = 28;
   private static final int HIDDEN_2 = 18;

   private final int inputSize;
   private final int outputSize;

   private final double[][] W1;
   private final double[] b1;
   private final double[][] W2;
   private final double[] b2;
   private final double[][] W3;
   private final double[] b3;

   public SmileMLP(int inputSize, int outputSize) {
      this.inputSize = inputSize;
      this.outputSize = outputSize;
      this.W1 = new double[inputSize][HIDDEN_1];
      this.b1 = new double[HIDDEN_1];
      this.W2 = new double[HIDDEN_1][HIDDEN_2];
      this.b2 = new double[HIDDEN_2];
      this.W3 = new double[HIDDEN_2][outputSize];
      this.b3 = new double[outputSize];
      xavierInit(W1, b1, inputSize, HIDDEN_1);
      xavierInit(W2, b2, HIDDEN_1, HIDDEN_2);
      xavierInit(W3, b3, HIDDEN_2, outputSize);
   }

   private static void xavierInit(double[][] w, double[] b, int fanIn, int fanOut) {
      double range = Math.sqrt(6.0 / (fanIn + fanOut));
      ThreadLocalRandom rng = ThreadLocalRandom.current();
      for (int i = 0; i < w.length; i++) {
         for (int j = 0; j < w[i].length; j++) {
            w[i][j] = rng.nextDouble(-range, range);
         }
      }
      for (int i = 0; i < b.length; i++) {
         b[i] = 0.0;
      }
   }

   /**
    * Прямой проход. Возвращает Q-значения для всех действий.
    */
   public float[] forward(float[] x) {
      double[] h1 = new double[HIDDEN_1];
      for (int j = 0; j < HIDDEN_1; j++) {
         double sum = b1[j];
         for (int i = 0; i < inputSize; i++) {
            sum += x[i] * W1[i][j];
         }
         h1[j] = Math.tanh(sum);
      }

      double[] h2 = new double[HIDDEN_2];
      for (int k = 0; k < HIDDEN_2; k++) {
         double sum = b2[k];
         for (int j = 0; j < HIDDEN_1; j++) {
            sum += h1[j] * W2[j][k];
         }
         h2[k] = Math.tanh(sum);
      }

      float[] out = new float[outputSize];
      for (int l = 0; l < outputSize; l++) {
         double sum = b3[l];
         for (int k = 0; k < HIDDEN_2; k++) {
            sum += h2[k] * W3[k][l];
         }
         out[l] = (float) sum;
      }
      return out;
   }

   /**
    * Обучение на батче с произвольными целевыми векторами (MSE).
    * X[i] - вход, Y[i] - целевой вектор длины outputSize.
    */
   public void trainOnBatch(float[][] X, float[][] Y, int epochs, float lr) {
      if (X == null || Y == null || X.length == 0 || X.length != Y.length) return;
      double learningRate = Math.max(1e-5, lr);
      int n = X.length;

      for (int ep = 0; ep < epochs; ep++) {
         // Градиенты
         double[][] dW1 = new double[inputSize][HIDDEN_1];
         double[] db1 = new double[HIDDEN_1];
         double[][] dW2 = new double[HIDDEN_1][HIDDEN_2];
         double[] db2 = new double[HIDDEN_2];
         double[][] dW3 = new double[HIDDEN_2][outputSize];
         double[] db3 = new double[outputSize];

         for (int s = 0; s < n; s++) {
            float[] x = X[s];
            float[] target = Y[s];

            // Forward
            double[] z1 = new double[HIDDEN_1];
            double[] a1 = new double[HIDDEN_1];
            for (int j = 0; j < HIDDEN_1; j++) {
               double sum = b1[j];
               for (int i = 0; i < inputSize; i++) {
                  sum += x[i] * W1[i][j];
               }
               z1[j] = sum;
               a1[j] = Math.tanh(sum);
            }

            double[] z2 = new double[HIDDEN_2];
            double[] a2 = new double[HIDDEN_2];
            for (int k = 0; k < HIDDEN_2; k++) {
               double sum = b2[k];
               for (int j = 0; j < HIDDEN_1; j++) {
                  sum += a1[j] * W2[j][k];
               }
               z2[k] = sum;
               a2[k] = Math.tanh(sum);
            }

            double[] out = new double[outputSize];
            for (int l = 0; l < outputSize; l++) {
               double sum = b3[l];
               for (int k = 0; k < HIDDEN_2; k++) {
                  sum += a2[k] * W3[k][l];
               }
               out[l] = sum;
            }

            // Backprop
            double[] dOut = new double[outputSize];
            for (int l = 0; l < outputSize; l++) {
               dOut[l] = 2.0 * (out[l] - target[l]) / n;
            }

            double[] dA2 = new double[HIDDEN_2];
            for (int k = 0; k < HIDDEN_2; k++) {
               double sum = 0.0;
               for (int l = 0; l < outputSize; l++) {
                  sum += dOut[l] * W3[k][l];
               }
               dA2[k] = sum * (1.0 - a2[k] * a2[k]);
            }

            double[] dA1 = new double[HIDDEN_1];
            for (int j = 0; j < HIDDEN_1; j++) {
               double sum = 0.0;
               for (int k = 0; k < HIDDEN_2; k++) {
                  sum += dA2[k] * W2[j][k];
               }
               dA1[j] = sum * (1.0 - a1[j] * a1[j]);
            }

            // Accumulate gradients
            for (int l = 0; l < outputSize; l++) {
               for (int k = 0; k < HIDDEN_2; k++) {
                  dW3[k][l] += dOut[l] * a2[k];
               }
               db3[l] += dOut[l];
            }

            for (int k = 0; k < HIDDEN_2; k++) {
               for (int j = 0; j < HIDDEN_1; j++) {
                  dW2[j][k] += dA2[k] * a1[j];
               }
               db2[k] += dA2[k];
            }

            for (int j = 0; j < HIDDEN_1; j++) {
               for (int i = 0; i < inputSize; i++) {
                  dW1[i][j] += dA1[j] * x[i];
               }
               db1[j] += dA1[j];
            }
         }

         // Update weights
         for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < HIDDEN_1; j++) {
               W1[i][j] -= learningRate * dW1[i][j];
            }
         }
         for (int j = 0; j < HIDDEN_1; j++) {
            b1[j] -= learningRate * db1[j];
         }
         for (int j = 0; j < HIDDEN_1; j++) {
            for (int k = 0; k < HIDDEN_2; k++) {
               W2[j][k] -= learningRate * dW2[j][k];
            }
         }
         for (int k = 0; k < HIDDEN_2; k++) {
            b2[k] -= learningRate * db2[k];
         }
         for (int k = 0; k < HIDDEN_2; k++) {
            for (int l = 0; l < outputSize; l++) {
               W3[k][l] -= learningRate * dW3[k][l];
            }
         }
         for (int l = 0; l < outputSize; l++) {
            b3[l] -= learningRate * db3[l];
         }
      }
   }

   public SmileMLP deepCopy() {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
         }
         try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (SmileMLP) ois.readObject();
         }
      } catch (Exception e) {
         SmileMLP copy = new SmileMLP(inputSize, outputSize);
         return copy;
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

   public static SmileMLP load(File file) {
      if (file == null || !file.exists()) return null;
      try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
         Object obj = ois.readObject();
         if (obj instanceof SmileMLP) {
            return (SmileMLP) obj;
         }
      } catch (IOException | ClassNotFoundException ignored) {
      }
      return null;
   }

   public int getInputSize() { return inputSize; }
   public int getOutputSize() { return outputSize; }
}
