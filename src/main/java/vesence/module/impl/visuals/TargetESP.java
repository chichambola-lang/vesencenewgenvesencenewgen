package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.Vesence;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.TriggerBot;
import vesence.renderengine.utils.animation.Animation;
import vesence.renderengine.utils.animation.Direction;
import vesence.renderengine.utils.animation.impl.EaseInOutQuad;
import vesence.utils.other.Mathf;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

@IModule(name = "TargetESP", description = "Жозки таргет есп", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class TargetESP extends Module {
    public static ModeSetting typeTargetEsp = new ModeSetting("Режим", "Картинка", "Картинка", "Призраки", "Призраки 2", "Кольцо",
            "Кубики", "Кристаллы", "Кристаллы 2", "Кольцо 2");
    public static BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false);
   public static ModeSetting typeImage = new ModeSetting("Режим картинки", "Клиент", "Клиент", "Ромб", "Ромб 2", "Ромб 3")
           .hidden(() -> !typeTargetEsp.is("Картинка"));
   public static ModeSetting typeCube = new ModeSetting("Режим кубиков", "Новый", "Новый", "Старый")
           .hidden(() -> !typeTargetEsp.is("Кубики"));
   private static final Identifier TARGET_TEXTURE = Identifier.of("vesence", "textures/world/target.png");
   private static final Identifier TARGET_TEXTURE_C = Identifier.of("vesence", "images/world/targetesp_2.png");
   private static final Identifier TARGET_TEXTURE_N = Identifier.of("vesence", "images/world/targetesp_3.png");
   private static final Identifier TARGET_TEXTURE_V = Identifier.of("vesence", "images/world/cube.png");
   private static final Identifier GLOW_TEXTURE = Identifier.of("vesence", "textures/world/glow.png");
   private static final Identifier GLOW_TEXTURE_C = Identifier.of("vesence", "textures/world/dashbloom.png");
   private static final Identifier CRYSTAL_GLOW_TEXTURE = Identifier.of("vesence", "textures/world/dashbloom.png");
   public static Animation2 alpha = new Animation2();
   public static Animation2 size = new Animation2();
   private LivingEntity lastTarget = null;
   private static long lastTime = 0L;
   private float animationNurik = 0.0F;
   private long currentTimeSpirits = 0L;
   private final ArrayList<TargetESP.OldCubeParticle> oldCubeParticles = new ArrayList<>();
   private static long oldCubeLastTime = System.currentTimeMillis();
   private static float oldCubeDeltaTime = 0.0F;
   private static final long OLD_CUBE_LIFE_TIME = 1000L;
   private static final int OLD_CUBE_PARTICLES_PER_SPAWN = 1;
   private static final float OLD_CUBE_SPAWN_INTERVAL = 0.02F;
   private static final int MAX_PARTICLES = 50;
   private float oldCubeSpawnAccumulator = 0.0F;

   private float spiritsRingAngle = 0.0F;
   private long spiritsRingLastTime = 0L;

   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
   private static final int RING2_TOTAL_POINTS = 138;
   private static final int RING2_TAIL_SEGMENTS = 26;
   private static final float RING2_HEAD_SIZE = 0.25f;
   private static final float[] RING2_COS = new float[RING2_TOTAL_POINTS];
   private static final float[] RING2_SIN = new float[RING2_TOTAL_POINTS];
   private static final float[] RING2_TAIL_PROGRESS = new float[RING2_TAIL_SEGMENTS];
   private static final float[] RING2_TAIL_ALPHA = new float[RING2_TAIL_SEGMENTS];
   private static final float[] RING2_TAIL_SIZE = new float[RING2_TAIL_SEGMENTS];

   static {
      for (int i = 0; i < RING2_TOTAL_POINTS; i++) {
         double angleRadians = 2.0D * Math.PI * i / RING2_TOTAL_POINTS;
         RING2_COS[i] = (float) Math.cos(angleRadians);
         RING2_SIN[i] = (float) Math.sin(angleRadians);
      }
      for (int i = 0; i < RING2_TAIL_SEGMENTS; i++) {
         float progress = (i + 1.0F) / (RING2_TAIL_SEGMENTS + 1.0F);
         RING2_TAIL_PROGRESS[i] = progress;
         RING2_TAIL_ALPHA[i] = (1.0F - progress) * 0.15F;
         RING2_TAIL_SIZE[i] = 1.0F - progress * 0.9F;
      }
   }

   private final Animation2 ring2InterpolationAnimation = new Animation2();
   private float ring2Alpha;
   private float previousRing2Alpha;
   private float ring2Step;
   private final int[] ring2TailColors = new int[RING2_TAIL_SEGMENTS];
   private final float[] ring2TailYOffsetFactors = new float[RING2_TAIL_SEGMENTS];
   private static final float RING2_SPEED = 0.012F;
   private final Animation2 crystalAlphaAnimation = new Animation2();
   private final Animation2 hurtColorAnimation = new Animation2();

   private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "pipeline/world/textured_quads"))
                   .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderPipeline TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "pipeline/world/textured_quads"))
                   .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderPipeline RING_STRIP_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                   .withLocation(Identifier.of("minecraft", "rendertype_lequal_depth_test"))
                   .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLE_STRIP)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                   .withLocation(Identifier.of("minecraft", "rendertype_lines"))
                   .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINE_STRIP)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderLayer RING_STRIP_LAYER = RenderLayer.of("night_ring_strip", RenderSetup.builder(RING_STRIP_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderLayer RING_LINE_LAYER = RenderLayer.of("night_ring_line", RenderSetup.builder(RING_LINE_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderPipeline COLOR_QUADS_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "pipeline/world/color_quads"))
                   .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderLayer COLOR_QUADS_LAYER = RenderLayer.of("night_color_quads", RenderSetup.builder(COLOR_QUADS_PIPELINE).expectedBufferSize(1024).translucent().build());
   private static final RenderPipeline COLOR_LINES_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                   .withLocation(Identifier.of("minecraft", "rendertype_lines"))
                   .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.LINES)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderPipeline CUBE_LINES_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "targetesp_cube_lines"))
                   .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderLayer CUBE_LINES_LAYER = RenderLayer.of("targetesp_cube_lines", RenderSetup.builder(CUBE_LINES_PIPELINE).expectedBufferSize(1024).translucent().build());

   private static final RenderPipeline CRYSTAL_TRIANGLES_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "pipeline/world/crystal_triangles"))
                   .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final RenderLayer CRYSTAL_TRIANGLES_LAYER = RenderLayer.of("vesence_crystal_triangles", RenderSetup.builder(CRYSTAL_TRIANGLES_PIPELINE).expectedBufferSize(16384).translucent().build());

   private static final RenderPipeline TEXTURED_QUADS_ADDITIVE_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "pipeline/world/textured_quads_additive"))
                   .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
    private static final RenderLayer CRYSTAL_GLOW_LAYER = RenderLayer.of(
            "vesence_crystal_glow",
            RenderSetup.builder(TEXTURED_QUADS_ADDITIVE_PIPELINE)
                    .expectedBufferSize(4096)
                    .translucent()
                    .texture("Sampler0", CRYSTAL_GLOW_TEXTURE)
                    .build());

    private static final RenderPipeline CRYSTAL_TRIANGLES_THROUGH_WALLS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/crystal_triangles_through_walls"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());
    private static final RenderLayer CRYSTAL_TRIANGLES_THROUGH_WALLS_LAYER = RenderLayer.of(
            "vesence_crystal_triangles_through_walls",
            RenderSetup.builder(CRYSTAL_TRIANGLES_THROUGH_WALLS_PIPELINE)
                    .expectedBufferSize(16384)
                    .translucent()
                    .build());
    private static final RenderLayer CRYSTAL_GLOW_THROUGH_WALLS_LAYER = RenderLayer.of(
            "vesence_crystal_glow_through_walls",
            RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                    .expectedBufferSize(4096)
                    .translucent()
                    .texture("Sampler0", CRYSTAL_GLOW_TEXTURE)
                    .build());

    private static final RenderLayer RING2_GLOW_LAYER = RenderLayer.of(
           "vesence_ring2_glow",
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   .expectedBufferSize(524288)
                   .translucent()
                   .texture("Sampler0", GLOW_TEXTURE_C)
                   .build());
   private static final RenderLayer TARGET_TEXTURE_LAYER = RenderLayer.of(
           TARGET_TEXTURE.toString(),
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", TARGET_TEXTURE).build());
   private static final RenderLayer TARGET_TEXTURE_N_LAYER = RenderLayer.of(
           TARGET_TEXTURE_N.toString(),
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", TARGET_TEXTURE_N).build());
   private static final RenderLayer TARGET_TEXTURE_C_LAYER = RenderLayer.of(
           TARGET_TEXTURE_C.toString(),
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", TARGET_TEXTURE_C).build());
   private static final RenderLayer TARGET_TEXTURE_V_LAYER = RenderLayer.of(
           TARGET_TEXTURE_V.toString(),
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", TARGET_TEXTURE_V).build());
   private static final RenderLayer GLOW_NO_DEPTH_LAYER = RenderLayer.of(
           GLOW_TEXTURE_C.toString(),
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", GLOW_TEXTURE_C).build());
   private static final RenderLayer SPIRITS_GLOW_LAYER = RenderLayer.of(
           GLOW_TEXTURE.getNamespace(),
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   .expectedBufferSize(1024)
                   .translucent()
                   .texture("Sampler0", GLOW_TEXTURE)
                   .build());
   private static final RenderLayer SPIRITS_BLOOM_LAYER = RenderLayer.of(
           GLOW_TEXTURE_C.getNamespace(),
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   .expectedBufferSize(1024)
                   .translucent()
                   .texture("Sampler0", GLOW_TEXTURE_C)
                   .build());

   public TargetESP() {
      this.addSettings(new Setting[] { typeTargetEsp, typeImage, typeCube, throughWalls });
   }

   @EventInit
   public void onRender(EventRender3D e) {
      alpha.update();
      LivingEntity target = AttackAura.target != null ? AttackAura.target : TriggerBot.target;
      if (mc.world != null && mc.player != null) {
         AttackAura hitAura = (AttackAura) Vesence.get.manager.getModule(AttackAura.class);
         TriggerBot triggerBot = (TriggerBot) Vesence.get.manager.getModule(TriggerBot.class);
         if (hitAura != null || triggerBot != null) {
            alpha.run(target == null ? 0.0 : 1.0, 0.35F, Easings.QUART_OUT);
            if (alpha.getValue() > 0.0) {
               if (target != null) {
                  if (this.lastTarget != target) {
                     lastTime = 0L;
                     this.currentTimeSpirits = 0L;
                     this.animationNurik = 0.0F;
                     this.spiritsRingLastTime = 0L;
                     this.spiritsRingAngle = 0.0F;
                  }

                  this.lastTarget = target;
               }
               if (this.lastTarget != null && !typeTargetEsp.is("Не отображать")) {
                  BufferAllocator allocator = new BufferAllocator(1048576);
                  Immediate immediate = VertexConsumerProvider.immediate(allocator);

                  try {
                     if (typeTargetEsp.is("Картинка") && typeImage.is("Ромб")) {
                        this.renderDiamond(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Картинка") && typeImage.is("Клиент")) {
                        this.renderDiamondNewStyle(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Картинка") && typeImage.is("Ромб 2")) {
                        this.renderDiamondNewStyle2(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }
                     if (typeTargetEsp.is("Картинка") && typeImage.is("Ромб 3")) {
                        this.renderDiamondNewStyle3(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Призраки")) {
                         this.renderSpirits(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Призраки 2")) {
                         this.renderSpiritsRing(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кольцо")) {
                        this.renderRing(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кубики") && typeCube.is("Новый")) {
                        this.renderCubes(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кубики") && typeCube.is("Старый")) {
                        this.renderCubesOld(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                      if (typeTargetEsp.is("Кристаллы")) {
                         this.renderCrystals(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                      }

                      if (typeTargetEsp.is("Кристаллы 2")) {
                         this.renderCrystals2(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                      }

                      if (typeTargetEsp.is("Кольцо 2")) {
                        this.renderRing2(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     immediate.draw();
                  } finally {
                     allocator.close();
                  }
               }
            } else {
               this.lastTarget = null;
               lastTime = 0L;
               this.currentTimeSpirits = 0L;
               this.animationNurik = 0.0F;
               this.spiritsRingLastTime = 0L;
               this.spiritsRingAngle = 0.0F;
            }
         }
      }
   }

   private void renderDiamond(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 900.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.7F - 0.9F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_TEXTURE_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.push();
      matrices.scale(2.0F, 2.0F, 1.0F);
      Matrix4f bloomMat = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuf = immediate.getBuffer(GLOW_NO_DEPTH_LAYER);
      drawGradientQuad(bloomBuf, bloomMat, colorS, (int) (255.0F * sizePC * 0.4F));
      matrices.pop();
      matrices.pop();
   }

   private void renderDiamondNewStyle(MatrixStack matrices, Immediate immediate, LivingEntity target,
                                      float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 1600.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.5F - 0.9F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_TEXTURE_N_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.push();
      matrices.scale(2.0F, 2.0F, 1.0F);
      Matrix4f bloomMat = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuf = immediate.getBuffer(GLOW_NO_DEPTH_LAYER);
      drawGradientQuad(bloomBuf, bloomMat, colorS, (int) (255.0F * sizePC * 0.4F));
      matrices.pop();
      matrices.pop();
   }

   private void renderDiamondNewStyle2(MatrixStack matrices, Immediate immediate, LivingEntity target,
                                       float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 1000.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.25F - 0.6F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_TEXTURE_C_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.push();
      matrices.scale(2.0F, 2.0F, 1.0F);
      Matrix4f bloomMat = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuf = immediate.getBuffer(GLOW_NO_DEPTH_LAYER);
      drawGradientQuad(bloomBuf, bloomMat, colorS, (int) (255.0F * sizePC * 0.4F));
      matrices.pop();
      matrices.pop();
   }
   private void renderDiamondNewStyle3(MatrixStack matrices, Immediate immediate, LivingEntity target,
                                       float partialTicks) {
      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      double x = lerpedPos.x;
      double y = lerpedPos.y;
      double z = lerpedPos.z;
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      matrices.push();
      matrices.translate(x - cameraPos.x, y - cameraPos.y + target.getHeight() / 1.75F, z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
      long currentTimeMillis = System.currentTimeMillis();
      float rotate = (float) Mathf.clamp(0.0, 720.0, (Math.sin(currentTimeMillis / 1000.0) + 1.0) / 2.0 * 360.0 * 2.0);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotate));
      TargetESP.size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      TargetESP.size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float rzs = TargetESP.size.get();
      float sizePC = (float) alpha.getValue();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * sizePC));
      int colorS = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.fade(), sizePC), redColor, TargetESP.size.get());
      float size = 1.25F - 0.6F * sizePC + (0.35F - 0.35F * rzs);
      matrices.scale(size, size, 1.0F);
      Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuffer = immediate.getBuffer(TARGET_TEXTURE_V_LAYER);
      drawGradientQuad(bloomBuffer, bloomMatrix, colorS, (int) (255.0F * sizePC));
      matrices.push();
      matrices.scale(2.0F, 2.0F, 1.0F);
      Matrix4f bloomMat = matrices.peek().getPositionMatrix();
      VertexConsumer bloomBuf = immediate.getBuffer(GLOW_NO_DEPTH_LAYER);
      drawGradientQuad(bloomBuf, bloomMat, colorS, (int) (255.0F * sizePC * 0.4F));
      matrices.pop();
      matrices.pop();
   }

   private void renderRing(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         double x = target.lastRenderX + (target.getX() - target.lastRenderX) * partialTicks;
         double y = target.lastRenderY + (target.getY() - target.lastRenderY) * partialTicks;
         double z = target.lastRenderZ + (target.getZ() - target.lastRenderZ) * partialTicks;
         matrices.push();
         matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
         float alphaPC = (float) alpha.getValue();
         float height = target.getHeight();
         double width = target.getWidth() * 1.0F - 0.2F * size.get();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         Matrix4f matrix = matrices.peek().getPositionMatrix();
         double duration = 1800.0;
         double elapsed = System.currentTimeMillis() % duration;
         double t = elapsed / duration;
         double progress = 0.5 + 0.5 * Math.sin(t * 2.0 * Math.PI - Math.PI / 2.0);
         double eased = height / 1.25F * 0.3 * Math.cos(t * 2.0 * Math.PI);
         VertexConsumer strip = immediate.getBuffer(RING_STRIP_LAYER);

         for (int i = 0; i <= 360; i += 5) {
            double rad = Math.toRadians(i);
            float xPos = (float) (Math.cos(rad) * width);
            float zPos = (float) (Math.sin(rad) * width);
            int c = ColorUtil.overCol(
                    ColorUtil.multAlpha(
                            ColorUtil.gradient(ColorUtil.multDark(ColorUtil.fade(), 0.5F),
                                    ColorUtil.multDark(ColorUtil.fade(), 1.0F), i * 4, 1),
                            alphaPC),
                    redColor,
                    size.get());
            int r = c >> 16 & 0xFF;
            int g = c >> 8 & 0xFF;
            int b = c & 0xFF;
            strip.vertex(matrix, xPos, (float) (height * progress), zPos).color(r, g, b, (int) (180.0F * alphaPC));
            strip.vertex(matrix, xPos, (float) (height * progress + eased), zPos).color(r, g, b, 0);
         }

         VertexConsumer line = immediate.getBuffer(RING_LINE_LAYER);

         for (int i = 0; i <= 360; i += 5) {
            double rad = Math.toRadians(i);
            float xPos = (float) (Math.cos(rad) * width);
            float zPos = (float) (Math.sin(rad) * width);
            int c = ColorUtil.overCol(
                    ColorUtil.multAlpha(
                            ColorUtil.gradient(ColorUtil.multDark(ColorUtil.fade(), 0.5F),
                                    ColorUtil.multDark(ColorUtil.fade(), 1.0F), i * 4, 1),
                            alphaPC),
                    redColor,
                    size.get());
            line.vertex(matrix, xPos, (float) (height * progress), zPos)
                    .color(ColorUtil.replAlpha(c, (int) (255.0F * alphaPC)));
         }

         matrices.pop();
      }
   }

   private void renderSpirits(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         long currentTime = System.currentTimeMillis();
         if (this.currentTimeSpirits == 0L) {
            this.currentTimeSpirits = currentTime;
         }

         long timeDiff = currentTime - this.currentTimeSpirits;
         if (timeDiff > 0L) {
            this.animationNurik += (float) (5L * timeDiff) / 1250.0f;
         }

         this.currentTimeSpirits = currentTime;
         Vec3d lerpedPos = target.getLerpedPos(partialTicks);
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         double x = lerpedPos.x - cameraPos.x;
         double y = lerpedPos.y - cameraPos.y;
         double z = lerpedPos.z - cameraPos.z;
         float alphaPC = (float) alpha.getValue();
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int fadeColor = ColorUtil.fade();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
         int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);
         VertexConsumer buffer = immediate.getBuffer(SPIRITS_GLOW_LAYER);
         int n2 = 3;
         int n3 = 12;
         int n4 = 3 * n2;
         matrices.push();
         Camera camera = mc.gameRenderer.getCamera();

         for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
               float f2 = this.animationNurik + j * 0.1F;
               float f3 = 0.75F;
               float f4 = 0.5F;
               int n5 = (int) Math.pow(i, 2.0);
               matrices.push();
               double particleX = x + f3 * Math.sin(f2 + n5);
               double particleY = y + f4 + 0.3F * Math.sin(this.animationNurik + j * 0.2F) + 0.2F * i;
               double particleZ = z + f3 * Math.cos(f2 - n5);
               matrices.translate(particleX, particleY, particleZ);

               float scale;
               scale = 0.0015F + j / 2000.0F;

               matrices.scale(scale, scale, scale);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
               Matrix4f matrix = matrices.peek().getPositionMatrix();
               VertexConsumer buffer2 = immediate.getBuffer(SPIRITS_GLOW_LAYER);
               int r = baseColor >> 16 & 0xFF;
               int g = baseColor >> 8 & 0xFF;
               int b = baseColor & 0xFF;
               int a = (int) (alphaPC * 255.0F);
               int n7 = -25;
               int n8 = 50;
               buffer2.vertex(matrix, n7, n7 + n8, 0.0F)
                       .color(r, g, b, a)
                       .texture(0.0F, 1.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               buffer2.vertex(matrix, n7 + n8, n7 + n8, 0.0F)
                       .color(r, g, b, a)
                       .texture(1.0F, 1.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               buffer2.vertex(matrix, n7 + n8, n7, 0.0F)
                       .color(r, g, b, a)
                       .texture(1.0F, 0.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               buffer2.vertex(matrix, n7, n7, 0.0F)
                       .color(r, g, b, a)
                       .texture(0.0F, 0.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               matrices.pop();
            }
         }

         for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
               float f2 = this.animationNurik + j * 0.1F;
               float f3 = 0.75F;
               float f4 = 0.5F;
               int n5 = (int) Math.pow(i, 2.0);
               matrices.push();
               double particleX = x + f3 * Math.sin(f2 + n5);
               double particleY = y + f4 + 0.3F * Math.sin(this.animationNurik + j * 0.2F) + 0.2F * i;
               double particleZ = z + f3 * Math.cos(f2 - n5);
               matrices.translate(particleX, particleY, particleZ);

               float bloomScale = (0.005F + j / 2000.0F) * 2;

               matrices.scale(bloomScale, bloomScale, bloomScale);
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
               Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
               VertexConsumer bloomBuffer = immediate.getBuffer(SPIRITS_BLOOM_LAYER);
               int br = baseColor >> 16 & 0xFF;
               int bg = baseColor >> 8 & 0xFF;
               int bb = baseColor & 0xFF;
               int ba = (int) (alphaPC * 255.0F * 0.2F);
               int bn7 = -25;
               int bn8 = 50;
               bloomBuffer.vertex(bloomMatrix, bn7, bn7 + bn8, 0.0F)
                       .color(br, bg, bb, ba)
                       .texture(0.0F, 1.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               bloomBuffer.vertex(bloomMatrix, bn7 + bn8, bn7 + bn8, 0.0F)
                       .color(br, bg, bb, ba)
                       .texture(1.0F, 1.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               bloomBuffer.vertex(bloomMatrix, bn7 + bn8, bn7, 0.0F)
                       .color(br, bg, bb, ba)
                       .texture(1.0F, 0.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               bloomBuffer.vertex(bloomMatrix, bn7, bn7, 0.0F)
                       .color(br, bg, bb, ba)
                       .texture(0.0F, 0.0F)
                       .overlay(OverlayTexture.DEFAULT_UV)
                       .light(15728880)
                       .normal(0.0F, 0.0F, 1.0F);
               matrices.pop();
            }
         }

         matrices.pop();
      }
   }

   private void renderSpiritsRing(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target == null) return;

      long currentTime = System.currentTimeMillis();
      if (this.spiritsRingLastTime == 0L) {
         this.spiritsRingLastTime = currentTime;
      }
      long timeDiff = currentTime - this.spiritsRingLastTime;
      if (timeDiff > 0L) {
         this.spiritsRingAngle += (float) (5L * timeDiff) / 600;
      }
      this.spiritsRingLastTime = currentTime;

      Vec3d lerpedPos = target.getLerpedPos(partialTicks);
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      double baseX = lerpedPos.x - cameraPos.x;
      double baseY = lerpedPos.y - cameraPos.y;
      double baseZ = lerpedPos.z - cameraPos.z;

      float alphaPC = (float) alpha.getValue();
      size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float atts = size.get();
      int fadeColor = ColorUtil.fade();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
      int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);

      Camera camera = mc.gameRenderer.getCamera();
      float entityHeight = target.getHeight();

      double duration = 2000;
      double elapsed = currentTime % duration;
      double t = elapsed / duration;
      double progress = 0.5 + 0.5 * Math.sin(t * 2.0 * Math.PI - Math.PI / 2.0);
      double eased = entityHeight / 1.0F * 0.3 * Math.cos(t * 2.0 * Math.PI);
      float ringY = (float) (entityHeight * progress + eased);

      int numGroups = 3;
      int particlesPerGroup = 12;
      float ringRadius = 0.65F;
      float arcLength = (float) (2.0 * Math.PI / numGroups * 0.4F);
      float angleStepPerParticle = arcLength / (particlesPerGroup - 1);
      int n7 = -25;
      int n8 = 50;

      matrices.push();

      for (int i = 0; i < numGroups; i++) {
         float groupOffset = (float) (2.0 * Math.PI / numGroups * i);

         for (int j = 0; j < particlesPerGroup; j++) {
            float angle = this.spiritsRingAngle + groupOffset + j * angleStepPerParticle;

            double particleX = baseX + ringRadius * Math.cos(angle);
            double particleZ = baseZ + ringRadius * Math.sin(angle);
            float bobY = 0.1F * (float) Math.sin(this.spiritsRingAngle + j * 0.2F);
            double particleY = baseY + ringY + bobY;

            int c = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.gradient(ColorUtil.multDark(fadeColor, 0.5F),ColorUtil.multDark(fadeColor, 1.0F), (i * particlesPerGroup + j) * 4, 1), alphaPC),
                    redColor, atts);

            int r = c >> 16 & 0xFF;
            int g = c >> 8 & 0xFF;
            int b = c & 0xFF;
            int a = (int) (alphaPC * 255.0F);

            float scale = 0.0022F + j / 2500.0F;

            matrices.push();
            matrices.translate(particleX, particleY, particleZ);
            matrices.scale(scale, scale, scale);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = immediate.getBuffer(SPIRITS_GLOW_LAYER);
            buffer.vertex(matrix, n7, n7 + n8, 0.0F)
                    .color(r, g, b, a)
                    .texture(0.0F, 1.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            buffer.vertex(matrix, n7 + n8, n7 + n8, 0.0F)
                    .color(r, g, b, a)
                    .texture(1.0F, 1.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            buffer.vertex(matrix, n7 + n8, n7, 0.0F)
                    .color(r, g, b, a)
                    .texture(1.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            buffer.vertex(matrix, n7, n7, 0.0F)
                    .color(r, g, b, a)
                    .texture(0.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            matrices.pop();

            float bloomScale = (scale + j / 2000.0F) * 1.5f;
            int ba = (int) (alphaPC * 255.0F * 0.2F);
            matrices.push();
            matrices.translate(particleX, particleY, particleZ);
            matrices.scale(bloomScale, bloomScale, bloomScale);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
            Matrix4f bloomMatrix = matrices.peek().getPositionMatrix();
            VertexConsumer bloomBuffer = immediate.getBuffer(SPIRITS_BLOOM_LAYER);
            bloomBuffer.vertex(bloomMatrix, n7, n7 + n8, 0.0F)
                    .color(r, g, b, ba)
                    .texture(0.0F, 1.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            bloomBuffer.vertex(bloomMatrix, n7 + n8, n7 + n8, 0.0F)
                    .color(r, g, b, ba)
                    .texture(1.0F, 1.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            bloomBuffer.vertex(bloomMatrix, n7 + n8, n7, 0.0F)
                    .color(r, g, b, ba)
                    .texture(1.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            bloomBuffer.vertex(bloomMatrix, n7, n7, 0.0F)
                    .color(r, g, b, ba)
                    .texture(0.0F, 0.0F)
                    .overlay(OverlayTexture.DEFAULT_UV)
                    .light(15728880)
                    .normal(0.0F, 0.0F, 1.0F);
            matrices.pop();
         }
      }

      matrices.pop();
   }

   private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      buffer.vertex(matrix, -0.5F, -0.5F, 0.0F)
              .color(r, g, b, alpha)
              .texture(0.0F, 1.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, 0.5F, -0.5F, 0.0F)
              .color(r, g, b, alpha)
              .texture(1.0F, 1.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, 0.5F, 0.5F, 0.0F)
              .color(r, g, b, alpha)
              .texture(1.0F, 0.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, -0.5F, 0.5F, 0.0F)
              .color(r, g, b, alpha)
              .texture(0.0F, 0.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
   }

   private void renderCubes(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
         long time = System.currentTimeMillis();
         int count = 24;
         double radius = 0.4 + target.getWidth() / 2.0F + 0.35F - 0.35F * alpha.get();
         double heightRange = target.getHeight();
         Vec3d lerpedPos = target.getLerpedPos(partialTicks);
         float alphaPC = (float) alpha.getValue();
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (60.0F * alphaPC));
         int fadeColor = ColorUtil.fade();
         int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35F);
         int color = ColorUtil.overCol(baseColor, redColor, atts);
         int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                 ColorUtil.getColor(200, 100, 100, (int) (255.0F * alphaPC)), atts);

         for (int i = 0; i < count; i++) {
            double r1 = Math.sin(i * 132.12 + 4.12);
            double r2 = Math.cos(i * 453.21 + 1.23);
            double r3 = Math.sin(i * 789.34 + 9.87);
            double speedFactor = 1.0;
            double angleOffset = (Math.PI * 2) / count * i;
            double timeFactor = time / 6000.0 * (Math.PI * 2) * speedFactor;
            double angle = timeFactor + angleOffset;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double ySpeed = 1.0 + r1 * 0.2;
            double yPhase = angleOffset + r3 * 2.0;
            double yOffset = Math.sin(time / 9000.0 * (Math.PI * 2) * ySpeed + yPhase) * 0.45 + 0.55;
            double y = yOffset * heightRange;
            double cX = lerpedPos.x + x - cameraPos.x;
            double cY = lerpedPos.y + y - cameraPos.y;
            double cZ = lerpedPos.z + z - cameraPos.z;
            matrices.push();
            matrices.translate(cX, cY, cZ);
            float pulse = 1.0F + 0.15F * (float) Math.sin(time / 400.0 + i * 1.5);
            float cubeSize = 0.19F * pulse;
            double hurtFactor = atts * (0.5 + 0.5 * Math.sin(i * 123.45));
            if (hurtFactor > 0.05) {
               cubeSize = (float) (cubeSize * (1.0 - hurtFactor * 0.2));
               double pushOut = hurtFactor * 0.4;
               matrices.translate(Math.cos(angle) * pushOut, 0.0, Math.sin(angle) * pushOut);
            }

            matrices.push();
            float selfRotSpeed = 12000.0F + (float) r3 * 2000.0F;
            float selfRot = (float) (time % (long) Math.abs(selfRotSpeed)) / Math.abs(selfRotSpeed) * 360.0F;
            if (i % 3 == 0) {
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfRot));
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(selfRot));
            } else if (i % 3 == 1) {
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfRot));
               matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(selfRot));
            } else {
               matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(selfRot));
               matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(selfRot));
            }

            VertexConsumer buffer = immediate.getBuffer(COLOR_QUADS_LAYER);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            drawColorCube(buffer, matrix, ColorUtil.multAlpha(color, 0.5F), cubeSize);
            int lineColor = ColorUtil.multAlpha(color, alphaPC * 1.0F);
            VertexConsumer lineBuffer = immediate.getBuffer(CUBE_LINES_LAYER);
            drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);
            matrices.pop();
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
            VertexConsumer glowBuffer = immediate.getBuffer(SPIRITS_BLOOM_LAYER);
            Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
            float glowSize = cubeSize * 3.0F;
            matrices.scale(glowSize, glowSize, glowSize);
            drawGradientQuad(glowBuffer, glowMatrix, glowCol, (int) (125.0F * alphaPC));
            matrices.pop();
            matrices.pop();
         }
      }
   }

   private static void drawColorCube(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
      float s = size / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      buffer.vertex(matrix, -s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, -s).color(r, g, b, a);
      buffer.vertex(matrix, -s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, -s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, -s).color(r, g, b, a);
      buffer.vertex(matrix, s, s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, s).color(r, g, b, a);
      buffer.vertex(matrix, s, -s, -s).color(r, g, b, a);
   }

   private static void drawCubeLines(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
      float s = size / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      drawLine(buffer, matrix, -s, -s, -s, s, -s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, -s, s, -s, s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, s, -s, -s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, s, -s, -s, -s, r, g, b, a);
      drawLine(buffer, matrix, -s, s, -s, s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, s, -s, s, s, s, r, g, b, a);
      drawLine(buffer, matrix, s, s, s, -s, s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, s, s, -s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, -s, -s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, -s, s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, s, s, s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, s, -s, s, s, r, g, b, a);
   }

   private static void drawLine(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2,
                                float y2, float z2, int r, int g, int b, int a) {
      buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
      buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
   }

   private void renderCubesOld(MatrixStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target == null) {
         this.oldCubeParticles.clear();
      } else {
         Iterator<TargetESP.OldCubeParticle> iterator = this.oldCubeParticles.iterator();

         while (iterator.hasNext()) {
            TargetESP.OldCubeParticle particle = iterator.next();
            if (particle.animation.getDirection() != Direction.FORWARDS && particle.animation.getOutput() <= 0.0F) {
               iterator.remove();
            }
         }

         long currentTime = System.currentTimeMillis();
         oldCubeDeltaTime = Math.max(0.001F, Math.min(0.1F, (float) (currentTime - oldCubeLastTime) / 1000.0F));
         oldCubeLastTime = currentTime;
         if (this.oldCubeParticles.size() < 50) {
            this.oldCubeSpawnAccumulator = this.oldCubeSpawnAccumulator + oldCubeDeltaTime;

            while (this.oldCubeSpawnAccumulator >= 0.02F && this.oldCubeParticles.size() < 50) {
               this.oldCubeSpawnAccumulator -= 0.02F;

               for (int i = 0; i < 1 && this.oldCubeParticles.size() < 50; i++) {
                  double rand = Mathf.random(0.0F, 360.0F);
                  double x = Math.cos(rand * Math.PI / 180.0) * 0.7F;
                  double y = Mathf.getRandomNumberBetween(0.04F, 0.2F);
                  double z = Math.sin(rand * Math.PI / 180.0) * 0.7F;
                  this.oldCubeParticles.add(new TargetESP.OldCubeParticle(target, x, y, z));
               }
            }
         }

         if (!this.oldCubeParticles.isEmpty()) {
            float alphaPC = (float) alpha.getValue();
            size.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
            size.run(hurtPC, 0.4F, Easings.QUART_OUT);
            float atts = size.get();
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (60.0F * alphaPC));
            int fadeColor = ColorUtil.fade();
            int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35F);
            int color = ColorUtil.overCol(baseColor, redColor, atts);
            int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                    ColorUtil.getColor(200, 100, 100, (int) (255.0F * alphaPC)), atts);
            Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
            float pitch = mc.gameRenderer.getCamera().getPitch();
            float yaw = mc.gameRenderer.getCamera().getYaw();
            for (TargetESP.OldCubeParticle particle : this.oldCubeParticles) {
               particle.update(partialTicks);
               particle.render(matrices, immediate, color, glowCol, alphaPC, atts, partialTicks, cameraPos, pitch, yaw,
                       SPIRITS_BLOOM_LAYER);
            }
         }
      }
   }

   private void renderCrystals(MatrixStack matrices, Immediate immediate, LivingEntity target, float tickDelta) {
      if (target == null) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      this.hurtColorAnimation.update();
      float easedAnim = easeOutCubic(alphaPC);

      Vec3d targetPos = target.getLerpedPos(tickDelta);
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      Vec3d renderPos = targetPos.subtract(cameraPos);

      float time = (mc.player.age + tickDelta) * 6.0F;
      float entityHeight = target.getHeight();
      float entityWidth = target.getWidth();
      float halfWidth = entityWidth * 0.5F;

      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      this.hurtColorAnimation.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float hurtBlend = (float) this.hurtColorAnimation.get();
      int baseColor = blendWithHurt(ColorUtil.fade(), ColorUtil.getColor(255, 0, 0, 255), hurtBlend);

      matrices.push();
      matrices.translate(renderPos.x, renderPos.y, renderPos.z);

      Camera camera = mc.gameRenderer.getCamera();

      VertexConsumer glowBuffer = immediate.getBuffer(CRYSTAL_GLOW_LAYER);

      int crystalCount = 14;
      for (int i = 0; i < crystalCount; i++) {
         float seed1 = (float) Math.sin(i * 1.7F + 0.3F) * 0.5F + 0.5F;
         float seed2 = (float) Math.cos(i * 2.3F + 0.7F) * 0.5F + 0.5F;
         float seed3 = (float) Math.sin(i * 3.1F + 1.1F) * 0.5F + 0.5F;
         float angleOffset = i * (360F / crystalCount) + seed1 * 12F;
         float angle = time + angleOffset;
         float radius = halfWidth + 0.25F + seed3 * 0.15F;

         float x = radius * (float) Math.cos(Math.toRadians(angle));
         float z = radius * (float) Math.sin(Math.toRadians(angle));
         float y = seed2 * entityHeight;
         float crystalScale = 0.24F * easedAnim;

         matrices.push();
         matrices.translate(x, y, z);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
         matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
         matrices.scale(crystalScale * 3.6F, crystalScale * 3.6F, crystalScale * 3.6F);
         Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
         int glowColor = ColorUtil.multAlpha(baseColor, alphaPC * 0.5F);
         drawGradientQuad(glowBuffer, glowMatrix, glowColor, (glowColor >> 24) & 0xFF);
         matrices.pop();
      }

      VertexConsumer geomBuffer = immediate.getBuffer(CRYSTAL_TRIANGLES_LAYER);
      for (int i = 0; i < crystalCount; i++) {
         float seed1 = (float) Math.sin(i * 1.7F + 0.3F) * 0.5F + 0.5F;
         float seed2 = (float) Math.cos(i * 2.3F + 0.7F) * 0.5F + 0.5F;
         float seed3 = (float) Math.sin(i * 3.1F + 1.1F) * 0.5F + 0.5F;
         float angleOffset = i * (360F / crystalCount) + seed1 * 12F;
         float angle = time + angleOffset;
         float radius = halfWidth + 0.25F + seed3 * 0.15F;

         float x = radius * (float) Math.cos(Math.toRadians(angle));
         float z = radius * (float) Math.sin(Math.toRadians(angle));
         float y = seed2 * entityHeight;

         drawCrystalGeometry(matrices, geomBuffer, x, y, z, 0.24F * easedAnim, angle, baseColor, alphaPC * 0.7F);
      }

      matrices.pop();
   }

   private void renderCrystals2(MatrixStack matrices, Immediate immediate, LivingEntity target, float tickDelta) {
      if (target == null) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      float easedAnim = easeOutCubic(alphaPC);

      Vec3d targetPos = target.getLerpedPos(tickDelta);
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      Vec3d renderPos = targetPos.subtract(cameraPos);

      float time = (mc.player.age + tickDelta) * 6.0F;
      float entityHeight = target.getHeight();
      float entityWidth = target.getWidth();
      float halfWidth = entityWidth * 0.5F;

      matrices.push();
      matrices.translate(renderPos.x, renderPos.y, renderPos.z);

      Camera camera = mc.gameRenderer.getCamera();
      boolean noDepth = throughWalls.get();
      VertexConsumer glowBuffer = immediate.getBuffer(noDepth ? CRYSTAL_GLOW_THROUGH_WALLS_LAYER : CRYSTAL_GLOW_LAYER);

      int baseColor = ColorUtil.fade();
      int crystalCount = 14;
      for (int i = 0; i < crystalCount; i++) {
         float seed1 = (float) Math.sin(i * 1.7F + 0.3F) * 0.5F + 0.5F;
         float seed2 = (float) Math.cos(i * 2.3F + 0.7F) * 0.5F + 0.5F;
         float seed3 = (float) Math.sin(i * 3.1F + 1.1F) * 0.5F + 0.5F;

         float angleOffset = i * (360F / crystalCount) + seed1 * 12F;
         float angle = time + angleOffset;
         float radius = halfWidth + 0.25F + seed3 * 0.15F;

         float x = radius * (float) Math.cos(Math.toRadians(angle));
         float z = radius * (float) Math.sin(Math.toRadians(angle));
         float y = seed2 * entityHeight;

         float crystalScale = 0.18F * easedAnim;

         drawCrystalB2(glowBuffer, matrices, camera, x, y, z, crystalScale * 3.6F, baseColor, alphaPC * 0.3F);
      }

      VertexConsumer geomBuffer = immediate.getBuffer(noDepth ? CRYSTAL_TRIANGLES_THROUGH_WALLS_LAYER : CRYSTAL_TRIANGLES_LAYER);

      for (int i = 0; i < crystalCount; i++) {
         float seed1 = (float) Math.sin(i * 1.7F + 0.3F) * 0.5F + 0.5F;
         float seed2 = (float) Math.cos(i * 2.3F + 0.7F) * 0.5F + 0.5F;
         float seed3 = (float) Math.sin(i * 3.1F + 1.1F) * 0.5F + 0.5F;

         float angleOffset = i * (360F / crystalCount) + seed1 * 12F;
         float angle = time + angleOffset;
         float radius = halfWidth + 0.25F + seed3 * 0.15F;

         float x = radius * (float) Math.cos(Math.toRadians(angle));
         float z = radius * (float) Math.sin(Math.toRadians(angle));
         float y = seed2 * entityHeight;

         float crystalScale = 0.18F * easedAnim;

         drawCrystalH2(geomBuffer, matrices, x, y, z, crystalScale, angle, baseColor, alphaPC * 0.7F);
      }

      matrices.pop();
   }

   private void drawCrystalB2(VertexConsumer buffer, MatrixStack matrices, Camera camera,
                               float x, float y, float z, float size, int color, float alpha) {
      matrices.push();
      matrices.translate(x, y, z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      int glowColor = ColorUtil.multAlpha(color, alpha);
      drawGradientQuad(buffer, matrix, glowColor, (glowColor >> 24) & 0xFF);

      matrices.pop();
   }

   private void drawCrystalH2(VertexConsumer buffer, MatrixStack matrices,
                               float x, float y, float z, float scale,
                               float yaw, int color, float alpha) {
      matrices.push();
      matrices.translate(x, y, z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw + 90F));
      matrices.scale(scale, scale, scale);
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      int r = (color >> 16) & 0xFF;
      int g = (color >> 8) & 0xFF;
      int b = color & 0xFF;
      int a = (int) (180 * alpha);

      int rL = Math.min(255, (int)(r * 1.3F));
      int gL = Math.min(255, (int)(g * 1.3F));
      int bL = Math.min(255, (int)(b * 1.3F));

      int rD = (int)(r * 0.6F);
      int gD = (int)(g * 0.6F);
      int bD = (int)(b * 0.6F);

      float w = 0.5F;
      float h = 1.0F;

      drawCrystalTriangle(buffer, matrix, 0, 0, h, -w, 0, 0, 0, w, 0, rL, gL, bL, a);
      drawCrystalTriangle(buffer, matrix, 0, 0, h, 0, w, 0, w, 0, 0, rL, gL, bL, a);
      drawCrystalTriangle(buffer, matrix, 0, 0, h, w, 0, 0, 0, -w, 0, r, g, b, a);
      drawCrystalTriangle(buffer, matrix, 0, 0, h, 0, -w, 0, -w, 0, 0, r, g, b, a);

      drawCrystalTriangle(buffer, matrix, 0, 0, -h, 0, w, 0, -w, 0, 0, rD, gD, bD, a);
      drawCrystalTriangle(buffer, matrix, 0, 0, -h, w, 0, 0, 0, w, 0, rD, gD, bD, a);
      drawCrystalTriangle(buffer, matrix, 0, 0, -h, 0, -w, 0, w, 0, 0, rD, gD, bD, a);
      drawCrystalTriangle(buffer, matrix, 0, 0, -h, -w, 0, 0, 0, -w, 0, rD, gD, bD, a);

      matrices.pop();
   }

   private void drawCrystalTriangle(VertexConsumer buffer, Matrix4f matrix,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     float x3, float y3, float z3,
                                     int r, int g, int b, int a) {
      buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
      buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
      buffer.vertex(matrix, x3, y3, z3).color(r, g, b, a);
   }

   private int getThemeColorAngle(int angle) {
      int baseColor = ColorUtil.fade();
      float hueShift = (angle % 360) / 360.0F * 0.15F;
      float[] hsv = Color.RGBtoHSB((baseColor >> 16) & 0xFF, (baseColor >> 8) & 0xFF, baseColor & 0xFF, null);
      hsv[0] = (hsv[0] + hueShift) % 1.0F;
      int shifted = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
      return (shifted & 0x00FFFFFF) | (baseColor & 0xFF000000);
   }

   private void drawCrystalGeometry(MatrixStack matrices, VertexConsumer buffer, float x, float y, float z, float scale, float yaw, int color, float alpha) {
      matrices.push();
      matrices.translate(x, y, z);
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw + 90F));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90F));
      matrices.scale(scale, scale, scale);
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      int r = (color >> 16) & 0xFF;
      int g = (color >> 8) & 0xFF;
      int b = color & 0xFF;

      int a = (int) (255 * alpha);
      int aDark = (int) (255 * alpha);

      int rL = Math.min(255, (int) (r * 0.10F));
      int gL = Math.min(255, (int) (g * 0.10F));
      int bL = Math.min(255, (int) (b * 0.10F));

      int rLDark = Math.min(255, (int) (r));
      int gLDark = Math.min(255, (int) (g));
      int bLDark = Math.min(255, (int) (b));

      float d = 0.3F;
      float h = 0.5F;

      float[][] vertices = {
              {0.0F, h, 0.0F},
              {0.0F, -h, 0.0F},
              {d, 0.0F, d},
              {-d, 0.0F, -d},
              {d, 0.0F, -d},
              {-d, 0.0F, d}
      };

      int[][] faces = {
              {0, 2, 5}, {0, 5, 3}, {0, 3, 4}, {0, 4, 2},
              {1, 2, 5}, {1, 5, 3}, {1, 3, 4}, {1, 4, 2}
      };

      for (int[] face : faces) {
         int i0 = face[0];
         int i1 = face[1];
         int i2 = face[2];

         buffer.vertex(matrix, vertices[i0][0], vertices[i0][1], vertices[i0][2]).color(rL, gL, bL, aDark);
         buffer.vertex(matrix, vertices[i1][0], vertices[i1][1], vertices[i1][2]).color(rL, gL, bL, aDark);
         buffer.vertex(matrix, vertices[i2][0], vertices[i2][1], vertices[i2][2]).color(rL, gL, bL, a);

         buffer.vertex(matrix, vertices[i0][0], vertices[i0][1], vertices[i0][2]).color(rL, gL, bL, a);
         buffer.vertex(matrix, vertices[i2][0], vertices[i2][1], vertices[i2][2]).color(rL, gL, bL, a);
         buffer.vertex(matrix, vertices[i1][0], vertices[i1][1], vertices[i1][2]).color(rLDark, gLDark, bLDark, aDark);
      }

      matrices.pop();
   }

   private void renderRing2(MatrixStack matrices, Immediate immediate, LivingEntity target, float tickDelta) {
      if (target == null) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      this.hurtColorAnimation.update();
      float easedAnim = easeOutCubic(alphaPC);

      Vec3d targetPos = target.getLerpedPos(tickDelta);
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      Vec3d renderPos = targetPos.subtract(cameraPos);

      float entityWidth = target.getWidth() * 0.9F;
      float entityHeight = target.getHeight();

      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      this.hurtColorAnimation.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float hurtBlend = (float) this.hurtColorAnimation.get();
      int baseColor = blendWithHurt(ColorUtil.fade(), ColorUtil.getColor(255, 0, 0, 255), hurtBlend);
      int headColor = ColorUtil.multAlpha(baseColor, easedAnim * 0.4F);

      double duration = 1800.0;
      double elapsed = System.currentTimeMillis() % duration;
      double t = elapsed / duration;
      double progress = 0.5 + 0.5 * Math.sin(t * 2.0 * Math.PI - Math.PI / 2.0);
      double eased = entityHeight / 1.25F * 0.3 * Math.cos(t * 2.0 * Math.PI);
      float headY = (float) (entityHeight * progress);
      float tailDeltaY = (float) eased;

      for (int i = 0; i < RING2_TAIL_SEGMENTS; i++) {
         ring2TailYOffsetFactors[i] = tailDeltaY * RING2_TAIL_PROGRESS[i];
         ring2TailColors[i] = ColorUtil.multAlpha(baseColor, easedAnim * RING2_TAIL_ALPHA[i]);
      }

      Vector3f rightAxis = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f upAxis = new Vector3f(0.0F, 1.0F, 0.0F);
      mc.gameRenderer.getCamera().getRotation().transform(rightAxis);
      mc.gameRenderer.getCamera().getRotation().transform(upAxis);

      matrices.push();
      matrices.translate(renderPos.x, renderPos.y, renderPos.z);
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      VertexConsumer buffer = immediate.getBuffer(RING2_GLOW_LAYER);

      for (int i = 0; i < RING2_TOTAL_POINTS; i++) {
         float localCenterX = RING2_COS[i] * entityWidth;
         float localCenterZ = RING2_SIN[i] * entityWidth;

         appendBillboardGlowQuad(buffer, matrix, localCenterX, headY, localCenterZ, RING2_HEAD_SIZE, rightAxis, upAxis, headColor);

         for (int t2 = 0; t2 < RING2_TAIL_SEGMENTS; t2++) {
            appendBillboardGlowQuad(
                    buffer, matrix,
                    localCenterX, headY + ring2TailYOffsetFactors[t2], localCenterZ,
                    RING2_HEAD_SIZE * RING2_TAIL_SIZE[t2],
                    rightAxis, upAxis,
                    ring2TailColors[t2]
            );
         }
      }

      matrices.pop();
   }

   private void appendBillboardGlowQuad(VertexConsumer buffer, Matrix4f matrix, float centerX, float centerY, float centerZ, float size, Vector3f rightAxis, Vector3f upAxis, int color) {
      if (size <= 0.0F || ((color >> 24) & 0xFF) == 0) return;

      float half = size * 0.5F;
      float rx = rightAxis.x * half;
      float ry = rightAxis.y * half;
      float rz = rightAxis.z * half;
      float ux = upAxis.x * half;
      float uy = upAxis.y * half;
      float uz = upAxis.z * half;

      buffer.vertex(matrix, centerX - rx + ux, centerY - ry + uy, centerZ - rz + uz)
              .texture(0.0F, 1.0F)
              .color(color)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, centerX + rx + ux, centerY + ry + uy, centerZ + rz + uz)
              .texture(1.0F, 1.0F)
              .color(color)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, centerX + rx - ux, centerY + ry - uy, centerZ + rz - uz)
              .texture(1.0F, 0.0F)
              .color(color)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
      buffer.vertex(matrix, centerX - rx - ux, centerY - ry - uy, centerZ - rz - uz)
              .texture(0.0F, 0.0F)
              .color(color)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(0.0F, 0.0F, 1.0F);
   }

   private float easeOutCubic(float x) {
      return (float) (1 - Math.pow(1 - x, 3));
   }

   private double absSinAnimation(double step) {
      return Math.abs(Math.sin(step)) * 0.8D + 0.1D;
   }

   private int blendWithHurt(int baseColor, int hurtColor, float hurtBlend) {
      return hurtBlend <= 0.0F ? baseColor : ColorUtil.overCol(baseColor, hurtColor, hurtBlend);
   }

   @Environment(EnvType.CLIENT)
   private static class OldCubeParticle {
      double x;
      double y;
      double z;
      double posX;
      double posY;
      double posZ;
      double motionX;
      double motionY;
      double motionZ;
      long time;
      LivingEntity entity;
      Animation animation = new EaseInOutQuad(500, 1.0);
      private double velocityY;

      public OldCubeParticle(LivingEntity entity, double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.entity = entity;
         this.time = System.currentTimeMillis();
         this.velocityY = Mathf.getRandomNumberBetween(0.01F, 0.04F);
      }

      public long getTime() {
         return this.time;
      }

      public void update(float partialTicks) {
         long currentTime = System.currentTimeMillis();
         long elapsed = currentTime - this.getTime();
         this.animation.setDirection(elapsed <= 800L ? Direction.FORWARDS : Direction.BACKWARDS);
         this.y = this.y + this.velocityY * (TargetESP.oldCubeDeltaTime * 60.0F);
         if (this.entity != null) {
            Vec3d lerpedPos = this.entity.getLerpedPos(partialTicks);
            this.motionX = this.x + lerpedPos.x;
            this.motionY = this.y + lerpedPos.y;
            this.motionZ = this.z + lerpedPos.z;
         }
      }

      public void render(
              MatrixStack matrixStack,
              Immediate immediate,
              int color,
              int glowCol,
              float alphaPC,
              float atts,
              float partialTicks,
              Vec3d cameraPos,
              float pitch,
              float yaw,
              RenderLayer glowLayer) {
         long currentTime = System.currentTimeMillis();
         double rotation = (currentTime - this.getTime()) / 10.0;
         this.posX = Mathf.interpolate(this.posX, this.motionX - cameraPos.x, 0.2F);
         this.posY = Mathf.interpolate(this.posY, this.motionY - cameraPos.y, 0.2F);
         this.posZ = Mathf.interpolate(this.posZ, this.motionZ - cameraPos.z, 0.2F);
         float animOutput = this.animation.getOutput();
         if (!(animOutput <= 0.0F)) {
            float pulse = 1.0F + 0.15F * (float) Math.sin((currentTime - this.getTime()) / 400.0);
            float cubeSize = 0.12F + 0.04F * animOutput;
            matrixStack.push();
            matrixStack.translate(this.posX, this.posY, this.posZ);
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rotation));
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) rotation));
            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rotation));
            Matrix4f matrix = matrixStack.peek().getPositionMatrix();
            int cubeColor = ColorUtil.multAlpha(color, 0.5F * animOutput);
            VertexConsumer buffer = immediate.getBuffer(TargetESP.COLOR_QUADS_LAYER);
            TargetESP.drawColorCube(buffer, matrix, cubeColor, cubeSize);
            int lineColor = ColorUtil.multAlpha(color, alphaPC * animOutput);
            VertexConsumer lineBuffer = immediate.getBuffer(TargetESP.CUBE_LINES_LAYER);
            TargetESP.drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);
            matrixStack.pop();
            matrixStack.push();
            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            VertexConsumer glowBuffer = immediate.getBuffer(glowLayer);
            Matrix4f glowMatrix = matrixStack.peek().getPositionMatrix();
            float glowSize = cubeSize * 3.0F;
            matrixStack.scale(glowSize, glowSize, glowSize);
            int glowColorWithAlpha = ColorUtil.reAlphaInt(glowCol, (int) (125.0F * alphaPC * animOutput));
            TargetESP.drawGradientQuad(glowBuffer, glowMatrix, glowColorWithAlpha,
                    ColorUtil.getAlpha(glowColorWithAlpha));
            matrixStack.pop();
            matrixStack.pop();
         }
      }
   }
}
