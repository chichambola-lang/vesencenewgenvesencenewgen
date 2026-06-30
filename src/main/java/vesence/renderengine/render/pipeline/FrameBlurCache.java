package vesence.renderengine.render.pipeline;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import vesence.Vesence;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.ShaderProgram;

import java.nio.ByteBuffer;

public final class FrameBlurCache {

   private static ShaderProgram shader;
   private static boolean initFailed = false;
   private static int vao;
   private static int vbo;

   private static final int[] tex = new int[2];
   private static final int[] fbo = new int[2];
   private static int bufW, bufH;

   private static int locTex = -1;
   private static int locTexel = -1;
   private static int locDir = -1;
   private static int locSigma = -1;

   private static long lastFrameId = -1L;
   private static float lastSigma = -1f;
   private static int resultTex = 0;
   private static int cachedWidth, cachedHeight;

   private FrameBlurCache() {
   }

   public static float sigmaForRadius(float blurRadius) {
      return Math.max(blurRadius * 0.85f, 2.0f);
   }

   private static boolean ensureInit() {
      if (shader != null && vao != 0) return true;
      if (initFailed) return false;
      try {
         shader = ShaderProgram.fromResources(
               "assets/vesence/shaders/core/blur_prepass.vsh",
               "assets/vesence/shaders/core/blur_prepass.fsh");
         locTex = shader.getUniformLocation("uTex");
         locTexel = shader.getUniformLocation("uTexel");
         locDir = shader.getUniformLocation("uDir");
         locSigma = shader.getUniformLocation("uSigma");

         int aPosLoc = GL20.glGetAttribLocation(shader.id(), "aPos");
         if (aPosLoc < 0) aPosLoc = 0;

         vao = GL30.glGenVertexArrays();
         vbo = GL15.glGenBuffers();
         GL30.glBindVertexArray(vao);
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
         float[] verts = {
               -1f, -1f,
                3f, -1f,
               -1f,  3f
         };
         GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
         GL20.glEnableVertexAttribArray(aPosLoc);
         GL20.glVertexAttribPointer(aPosLoc, 2, GL11.GL_FLOAT, false, 0, 0);
         GL30.glBindVertexArray(0);
         GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
         return true;
      } catch (Exception e) {
         System.err.println("FrameBlurCache init failed: " + e.getMessage());
         e.printStackTrace();
         initFailed = true;
         return false;
      }
   }

   private static void ensureTargets(int w, int h) {
      if (w == bufW && h == bufH && tex[0] != 0) return;
      releaseTargets();
      bufW = w;
      bufH = h;
      for (int i = 0; i < 2; i++) {
         tex[i] = GL11.glGenTextures();
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
         GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
         GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

         fbo[i] = GL30.glGenFramebuffers();
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo[i]);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex[i], 0);
         GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
      }
      GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
   }

   public static int prepareForRadius(float blurRadius) {

      return 0;
   }

   public static void captureLiquidBlurFrame() {

   }

   public static int getBlurredTexture() {
      return resultTex;
   }

   public static int getWidth() {
      return cachedWidth;
   }

   public static int getHeight() {
      return cachedHeight;
   }

   private static void releaseTargets() {
      for (int i = 0; i < 2; i++) {
         if (tex[i] != 0) {
            GL11.glDeleteTextures(tex[i]);
            tex[i] = 0;
         }
         if (fbo[i] != 0) {
            GL30.glDeleteFramebuffers(fbo[i]);
            fbo[i] = 0;
         }
      }
      bufW = 0;
      bufH = 0;
   }

   public static void invalidate() {
      lastFrameId = -1L;
      lastSigma = -1f;
      resultTex = 0;
   }

   public static void close() {
      releaseTargets();
      if (shader != null) {
         shader.delete();
         shader = null;
      }
      if (vao != 0) {
         GL30.glDeleteVertexArrays(vao);
         vao = 0;
      }
      if (vbo != 0) {
         GL15.glDeleteBuffers(vbo);
         vbo = 0;
      }
      initFailed = false;
      invalidate();
   }

   private static int extractGlTexId(Framebuffer framebuffer) {
      if (framebuffer == null) {
         return 0;
      }
      try {
         GpuTextureView view = framebuffer.getColorAttachmentView();
         if (view != null && !view.isClosed() && view.texture() instanceof GlTexture glTex) {
            return glTex.getGlId();
         }
      } catch (Exception ignored) {
      }
      try {
         if (framebuffer.getColorAttachment() instanceof GlTexture glTex) {
            return glTex.getGlId();
         }
      } catch (Exception ignored) {
      }
      return 0;
   }
}
