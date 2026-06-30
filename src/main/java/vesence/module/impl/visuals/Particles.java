package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.client.util.math.MatrixStack.Entry;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vesence.event.EventInit;
import vesence.event.impl.EventChangeWorld;
import vesence.event.impl.EventPacket;
import vesence.event.player.AttackEvent;
import vesence.event.player.EventMotion;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.TitleSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.other.StopWatch;
import vesence.utils.player.PlayerUtil;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.animation.util.Animation;
import vesence.utils.render.animation.util.Easings;
import vesence.module.api.Module;

@IModule(name = "Particles", description = "Кастомные частицы при попадании и ударах", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Particles extends Module {
   public static MultiBooleanSetting events = new MultiBooleanSetting(
           "Спавнить при", new BooleanSetting("Атаке", true), new BooleanSetting("Бросок", true),
           new BooleanSetting("В мире", false), new BooleanSetting("Тотем", true));
   public static ModeSetting particleBase = new ModeSetting("Тип партиклов", "1", "1", "2");
   public static ModeSetting particleMode = new ModeSetting(
           "Тип частиц", "Bloom", "Bloom", "Star", "Snow", "Heart", "Dollar", "Triangle", "Sakura", "Genshin", "Rhombus")
           .hidden(() -> particleBase.is("2"));
   public static ModeSetting particleMode2 = new ModeSetting("Тип частиц 2", "Dollar", "Dollar", "Bitcoin", "Star")
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting size = new SliderSetting("Размер", 0.5F, 0.0F, 1.0F, 0.1F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting attackTitle = new TitleSetting("Атака").hidden(() -> particleBase.is("2"));
   public static SliderSetting attackCount = new SliderSetting("Кол-во атака", 10, 1, 50, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting attackSpeed = new SliderSetting("Скорость атаки", 0.1F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting attackLifetime = new SliderSetting("Время атаки", 2.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting throwTitle = new TitleSetting("Бросок").hidden(() -> particleBase.is("2"));
   public static SliderSetting throwCount = new SliderSetting("Кол-во бросок", 15, 1, 50, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting throwSpeed = new SliderSetting("Скорость броска", 0.15F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting throwLifetime = new SliderSetting("Время броска", 3.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting totemTitle = new TitleSetting("Тотем").hidden(() -> particleBase.is("2"));
   public static SliderSetting totemCount = new SliderSetting("Кол-во тотем", 30, 1, 100, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting totemSpeed = new SliderSetting("Скорость тотема", 0.2F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting totemLifetime = new SliderSetting("Время тотема", 4.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting worldTitle = new TitleSetting("В мире").hidden(() -> particleBase.is("2"));
   public static SliderSetting worldCount = new SliderSetting("Кол-во мир", 5, 1, 50, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting worldSpeed = new SliderSetting("Скорость мира", 0.05F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting worldLifetime = new SliderSetting("Время мира", 5.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting attackTitle2 = new TitleSetting("Атака 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting attackCount2 = new SliderSetting("Кол-во атака 2", 10, 1, 50, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting attackSpeed2 = new SliderSetting("Скорость атаки 2", 0.5F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting attackLifetime2 = new SliderSetting("Время атаки 2", 4.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   public static TitleSetting throwTitle2 = new TitleSetting("Бросок 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting throwCount2 = new SliderSetting("Кол-во бросок 2", 15, 1, 50, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting throwSpeed2 = new SliderSetting("Скорость броска 2", 0.5F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting throwLifetime2 = new SliderSetting("Время броска 2", 4.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   public static TitleSetting totemTitle2 = new TitleSetting("Тотем 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting totemCount2 = new SliderSetting("Кол-во тотем 2", 30, 1, 100, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting totemSpeed2 = new SliderSetting("Скорость тотема 2", 0.5F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting totemLifetime2 = new SliderSetting("Время тотема 2", 4.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   public static TitleSetting worldTitle2 = new TitleSetting("В мире 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting worldCount2 = new SliderSetting("Кол-во мир 2", 5, 1, 50, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting worldSpeed2 = new SliderSetting("Скорость мира 2", 0.3F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting worldLifetime2 = new SliderSetting("Время мира 2", 5.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
   private long lastUpdateTime = System.nanoTime();
   private static final String PIPELINE_NAMESPACE = "vesence";
   private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                   .withLocation(Identifier.of("vesence", "textures/world/textured_quads"))
                   .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final Identifier BLOOM_TEXTURE = Identifier.of("vesence", "textures/world/dashbloom.png");
   private static final Identifier BLOOM_SOFT_TEXTURE = Identifier.of("vesence", "textures/world/dashbloomsample.png");

   public static TitleSetting glowTitle = new TitleSetting("Свечение");
   public static BooleanSetting glowEnabled = new BooleanSetting("Свечение", true);
   public static BooleanSetting glowLighting = new BooleanSetting("Подсветка", true)
           .hidden(() -> !glowEnabled.get());
   public static SliderSetting glowIntensity = new SliderSetting("Сила свечения", 0.6F, 0.0F, 1.5F, 0.05F, false)
           .hidden(() -> !glowEnabled.get());
   public static SliderSetting glowRadius = new SliderSetting("Радиус свечения", 2.5F, 1.0F, 6.0F, 0.1F, false)
           .hidden(() -> !glowEnabled.get());

   private static final Map<ParticleType, RenderLayer> RENDER_LAYER_CACHE = new ConcurrentHashMap<>();
   private static final Random CACHED_RANDOM = Random.create();
   private static RenderLayer cachedBloomLayer = null;
   private static RenderLayer cachedBloomSoftLayer = null;
   private final List<Particle> targetParticles = new ArrayList<>();
   private final List<Particle> worldParticles = new ArrayList<>();
   private final List<Particle> flameParticles = new ArrayList<>();
   private final List<Particle> totemParticles = new ArrayList<>();
   private final List<VegaParticle> vegaParticles = new ArrayList<>();
   private static final Vector3f REUSABLE_NORMAL = new Vector3f(0.0F, 0.0F, 1.0F);
   private long totemPopTime = 0L;
   private Entity totemPopEntity = null;
   private static final long TOTEM_POP_DURATION = 1500L;
   private BufferAllocator allocator = null;
   private Immediate immediate = null;

   public Particles() {
      this.addSettings(new Setting[] { events, particleBase, particleMode, particleMode2, size,
              attackTitle, attackCount, attackSpeed, attackLifetime,
              throwTitle, throwCount, throwSpeed, throwLifetime,
              totemTitle, totemCount, totemSpeed, totemLifetime,
              worldTitle, worldCount, worldSpeed, worldLifetime,
              attackTitle2, attackCount2, attackSpeed2, attackLifetime2,
              throwTitle2, throwCount2, throwSpeed2, throwLifetime2,
              totemTitle2, totemCount2, totemSpeed2, totemLifetime2,
              worldTitle2, worldCount2, worldSpeed2, worldLifetime2,
              glowTitle, glowEnabled, glowLighting, glowIntensity, glowRadius });
   }

   private void clear() {
      this.targetParticles.clear();
      this.flameParticles.clear();
      this.worldParticles.clear();
      this.totemParticles.clear();
      this.vegaParticles.clear();
   }

   private void spawnParticle(List<Particle> particles, Vec3d position, Vec3d velocity) {
      float particleSize = (float) (0.05F + size.get() * 0.2F);
      int color = ColorUtil.fade(particles.size() * 100);
      String var7 = particleMode.get();

      ParticleType type = switch (var7) {
         case "Heart" -> ParticleType.HEART;
         case "Star" -> ParticleType.STAR;
         case "Snow" -> ParticleType.SNOW;
         case "Bloom" -> ParticleType.BLOOM;
         case "Dollar" -> ParticleType.DOLLAR;
         case "Triangle" -> ParticleType.TRIANGLE;
         case "Sakura" -> ParticleType.SAKURA;
         case "Genshin" -> ParticleType.GEMINI;
         case "Rhombus" -> ParticleType.SIMS;
         default -> ParticleType.BLOOM;
      };
      particles.add(
              new Particle(
                      type,
                      position.add(0.0, particleSize, 0.0),
                      velocity,
                      particles.size(),
                      (int) Mathf.step(Mathf.randomValue(0.0F, 360.0F), 15.0),
                      color,
                      particleSize,
                      0.2F));
   }

   private void spawnTotemParticle(Vec3d position, Vec3d velocity, int color) {
      float particleSize = (float) (0.05F + size.get() * 0.2F);
      String var7 = particleMode.get();

      ParticleType type = switch (var7) {
         case "Heart" -> ParticleType.HEART;
         case "Star" -> ParticleType.STAR;
         case "Snow" -> ParticleType.SNOW;
         case "Bloom" -> ParticleType.BLOOM;
         case "Dollar" -> ParticleType.DOLLAR;
         case "Triangle" -> ParticleType.TRIANGLE;
         case "Sakura" -> ParticleType.SAKURA;
         case "Genshin" -> ParticleType.GEMINI;
         case "Rhombus" -> ParticleType.SIMS;
         default -> ParticleType.BLOOM;
      };
      totemParticles.add(
              new Particle(
                      type,
                      position.add(0.0, particleSize, 0.0),
                      velocity,
                      totemParticles.size(),
                      (int) Mathf.step(Mathf.randomValue(0.0F, 360.0F), 15.0),
                      color,
                      particleSize,
                      0.2F));
   }

   private void spawnVegaParticle(Vec3d position, long lifetime, float speed) {
      boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
      vegaParticles.add(new VegaParticle(position, lower, lifetime, speed));
   }

   @EventInit
   public void onEvent(AttackEvent event) {
      if (!this.enable) {
         return;
      }

      Entity target = event.getTarget();
      if (events.get("Атаке")) {
         if (particleBase.is("2")) {
            boolean lowestAndRotates = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
            float w = target.getWidth() / 2.0F;
            float h = target.getHeight();
            int count = attackCount2.get().intValue();
            for (int i = 0; i < count; i++) {
               Vec3d vec = new Vec3d(target.getX(), target.getY(), target.getZ()).add(
                       (-w) + (w * 2.0F) * Math.random(),
                       h * Math.random(),
                       (-w) + (w * 2.0F) * Math.random());
               VegaParticle vp = new VegaParticle(vec, lowestAndRotates,
                       (long)(attackLifetime2.get() * 1000), attackSpeed2.get().floatValue());
               vegaParticles.add(vp);
            }
            return;
         }
         int count = attackCount.get().intValue();
         for (int i = 0; i < count; i++) {
            this.spawnParticle(
                    this.targetParticles,
                    new Vec3d(target.getX(), target.getY() + Mathf.randomValue(0.0F, target.getHeight()), target.getZ()),
                    new Vec3d(
                            Mathf.randomValue(-1.0F, 1.0F) * attackSpeed.get() * 10,
                            Mathf.randomValue(-1.0F, 1.0F) * attackSpeed.get() * 10,
                            Mathf.randomValue(-1.0F, 1.0F) * attackSpeed.get() * 10));
         }
      }
   }

   @EventInit
   public void onEvent(EventMotion event) {
      if (!this.enable) {
         return;
      }

      if (events.get("Тотем")) {
         if (mc.player != null && totemPopEntity != null) {
            long timeSincePop = System.currentTimeMillis() - totemPopTime;
            if (totemPopTime > 0L && timeSincePop < TOTEM_POP_DURATION) {
               Entity popEntity = totemPopEntity;
               if (popEntity.isAlive()) {
                  int count = particleBase.is("2") ? totemCount2.get().intValue() : totemCount.get().intValue();
                  float speed = particleBase.is("2") ? totemSpeed2.get().floatValue() : totemSpeed.get().floatValue();
                  long lifetime = particleBase.is("2")
                          ? (long)(totemLifetime2.get() * 1000)
                          : (long)(totemLifetime.get() * 1000);

                  for (int i = 0; i < count; i++) {
                     boolean isGreen = i % 2 == 0;
                     int color = isGreen ? ColorUtil.getColor(125, 255, 125) : ColorUtil.getColor(255, 255, 125);

                     Vec3d pos = new Vec3d(
                             popEntity.getX() + MathHelper.nextDouble(CACHED_RANDOM, -0.3, 0.3),
                             popEntity.getY() + popEntity.getHeight() / 2 + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                             popEntity.getZ() + MathHelper.nextDouble(CACHED_RANDOM, -0.3, 0.3)
                     );
                     Vec3d velocity = new Vec3d(
                             MathHelper.nextDouble(CACHED_RANDOM, -1.0, 1.0) * 6.0 * speed,
                             MathHelper.nextDouble(CACHED_RANDOM, -1.0, 1.0) * 6.0 * speed,
                             MathHelper.nextDouble(CACHED_RANDOM, -1.0, 1.0) * 6.0 * speed
                     );

                     if (particleBase.is("2")) {
                        boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
                        VegaParticle vp = new VegaParticle(pos, lower, lifetime, speed);
                        vp.color = color;
                        vegaParticles.add(vp);
                     } else {
                        this.spawnTotemParticle(pos, velocity, color);
                     }
                  }
               }
            } else {
               totemPopEntity = null;
            }
         }
      }

      if (events.get("Бросок")) {
         if (mc.world == null) {
            return;
         }

         for (Entity entity : mc.world.getEntities()) {
            if ((entity instanceof EnderPearlEntity || entity instanceof ArrowEntity || entity instanceof TridentEntity)
                    && (!(entity instanceof TridentEntity trident) || !trident.isOnGround())) {
               boolean isMoving = entity.lastX != entity.getX()
                       || entity.lastY != entity.getY()
                       || entity.lastZ != entity.getZ();
               if (isMoving) {
                  Vec3d pos = new Vec3d(entity.getX(), entity.getY(), entity.getZ());

                  if (particleBase.is("2")) {
                     int count = throwCount2.get().intValue();
                     float speed = throwSpeed2.get().floatValue();
                     long lifetime = (long)(throwLifetime2.get() * 1000);
                     for (int i = 0; i < count; i++) {
                        boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
                        VegaParticle vp = new VegaParticle(
                                new Vec3d(
                                        pos.x + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.y + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.z + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2)),
                                lower, lifetime, speed);
                        vegaParticles.add(vp);
                     }
                  } else {
                     int count = throwCount.get().intValue();
                     for (int i = 0; i < count; i++) {
                        this.spawnParticle(
                                this.flameParticles,
                                new Vec3d(
                                        pos.x + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.y + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.z + MathHelper.nextDouble(CACHED_RANDOM, -0.2, 0.2)),
                                new Vec3d(
                                        MathHelper.nextDouble(CACHED_RANDOM, -1.0, 1.0) * throwSpeed.get() * 10,
                                        MathHelper.nextDouble(CACHED_RANDOM, -0.3, 0.3) * throwSpeed.get() * 10,
                                        MathHelper.nextDouble(CACHED_RANDOM, -1.0, 1.0) * throwSpeed.get() * 10));
                     }
                  }
               }
            }
         }
      }

      if (events.get("В мире")) {
         if (mc.world == null || mc.player == null) {
            return;
         }

         int r = 12;

         if (particleBase.is("2")) {
            int count = worldCount2.get().intValue();
            float speed = worldSpeed2.get().floatValue();
            long lifetime = (long)(worldLifetime2.get() * 1000);
            for (int i = 0; i < count; i++) {
               Vec3d additional = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                       .add(Mathf.randomValue((float) (-r), (float) r), 0.0, Mathf.randomValue((float) (-r), (float) r));
               BlockPos topPos = mc.world.getTopPosition(Type.MOTION_BLOCKING, BlockPos.ofFloored(additional));
               double x = topPos.getX() + Mathf.randomValue(0.0F, 1.0F);
               double z = topPos.getZ() + Mathf.randomValue(0.0F, 1.0F);
               double y = mc.player.getY() + Mathf.randomValue(mc.player.getHeight(), (float) r);
               Vec3d spawnPos = new Vec3d(x, y, z);

               while (!mc.world.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.world.getTopYInclusive()) {
                  spawnPos = spawnPos.add(0.0, 1.0, 0.0);
               }

               boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
               VegaParticle vp = new VegaParticle(spawnPos, lower, lifetime, speed);
               vegaParticles.add(vp);
            }
         } else {
            int count = worldCount.get().intValue();
            for (int i = 0; i < count; i++) {
               Vec3d additional = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                       .add(Mathf.randomValue((float) (-r), (float) r), 0.0, Mathf.randomValue((float) (-r), (float) r));
               BlockPos topPos = mc.world.getTopPosition(Type.MOTION_BLOCKING, BlockPos.ofFloored(additional));
               double x = topPos.getX() + Mathf.randomValue(0.0F, 1.0F);
               double z = topPos.getZ() + Mathf.randomValue(0.0F, 1.0F);
               double y = mc.player.getY() + Mathf.randomValue(mc.player.getHeight(), (float) r);
               Vec3d spawnPos = new Vec3d(x, y, z);

               while (!mc.world.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.world.getTopYInclusive()) {
                  spawnPos = spawnPos.add(0.0, 1.0, 0.0);
               }

               this.spawnParticle(
                       this.worldParticles,
                       spawnPos,
                       new Vec3d(
                               mc.player.getVelocity().x + Mathf.randomValue(-2.0F, 2.0F) * worldSpeed.get() * 10,
                               Mathf.randomValue(-0.2, 0.2) * worldSpeed.get() * 10,
                               mc.player.getVelocity().z + Mathf.randomValue(-2.0F, 2.0F) * worldSpeed.get() * 10));
            }
         }
      }

      this.removeExpiredParticles(this.targetParticles, (long)(attackLifetime.get() * 1000));
      this.removeExpiredParticles(this.flameParticles, (long)(throwLifetime.get() * 1000));
      this.removeExpiredParticles(this.worldParticles, (long)(worldLifetime.get() * 1000));
      this.removeExpiredParticles(this.totemParticles, (long)(totemLifetime.get() * 1000));

      for (int i = 0; i < this.vegaParticles.size(); i++) {
         VegaParticle part = this.vegaParticles.get(i);
         if (part == null) continue;
         part.updatePart();
         if (part.toRemove) {
            this.vegaParticles.remove(i--);
         }
      }
   }

   @EventInit
   public void onEvent(EventPacket event) {
      if (!this.enable) return;
      if (events.get("Тотем") && event.getType() == EventPacket.Type.RECEIVE) {
         if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket) {
            if (statusPacket.getStatus() == 35) {
               Entity entity = statusPacket.getEntity(mc.world);
               if (entity != null) {
                  totemPopTime = System.currentTimeMillis();
                  totemPopEntity = entity;
               }
            }
         }
      }
   }

   @EventInit
   public void onEvent(EventRender3D event) {
      MatrixStack matrix = event.getMatrixStack();
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      long now = System.nanoTime();
      double deltaTime = (now - this.lastUpdateTime) / 1.0E9;
      this.lastUpdateTime = now;

      if (allocator == null) {
         allocator = new BufferAllocator(262144);
         immediate = VertexConsumerProvider.immediate(allocator);
      }

      try {
         if (!particleBase.is("2")) {
            this.renderParticles(matrix, immediate, cameraPos, this.targetParticles, 400L, 600L, deltaTime);
            this.renderParticles(matrix, immediate, cameraPos, this.flameParticles, 700L, 1200L, deltaTime);
            this.renderParticles(matrix, immediate, cameraPos, this.worldParticles, 1500L, 2200L, deltaTime);
            this.renderParticles(matrix, immediate, cameraPos, this.totemParticles, 1000L, 2000L, deltaTime);
         }
         this.renderVegaParticles(matrix, immediate, cameraPos, deltaTime);
         immediate.draw();
      } catch (Exception e) {
         if (allocator != null) { try { allocator.close(); } catch (Exception ignored) {} }
         allocator = null;
         immediate = null;
      }
   }

   @Override
   public void onDisable() {
      this.clear();
      if (allocator != null) {
         try { allocator.close(); } catch (Exception ignored) {}
         allocator = null;
         immediate = null;
      }
   }

   private void removeExpiredParticles(List<Particle> particles, long lifespan) {
      particles.removeIf(particle -> particle.time().finished(lifespan) && particle.animation.get() <= 0.0F);
   }

   private void renderParticles(
           MatrixStack matrix, Immediate immediate, Vec3d cameraPos, List<Particle> particles, long fadeInTime,
           long fadeOutTime, double deltaTime) {
      if (!particles.isEmpty()) {
         matrix.push();

         for (Particle particle : particles) {
            particle.update(true, deltaTime);
            boolean notFinishedFadeIn = !particle.time().finished(fadeInTime);
            boolean finishedFadeOut = particle.time().finished(fadeOutTime);
            if (notFinishedFadeIn) {
               particle.animation().run(1.0, 0.4, Easings.QUAD_OUT, true);
            } else if (finishedFadeOut) {
               particle.animation().run(0.0, 0.4, Easings.QUAD_OUT, true);
            }

            if (particle.animation.isAlive()) {
               particle.animation.update();
            }

            float animValue = particle.animation.get();
            int alpha = (int) (animValue * 255.0F);
            if (alpha > 0) {
               int color = ColorUtil.replAlpha(particle.color(), alpha);
               this.renderParticle(matrix, immediate, particle, (float) particle.posX, (float) particle.posY, (float) particle.posZ, particle.size,
                       color, alpha);
            }
         }

         matrix.pop();
      }
   }

   private void renderParticle(MatrixStack matrix, Immediate immediate, Particle particle, float x, float y,
                               float z, float pos, int color, int alpha) {
      matrix.push();
      Renderer2D.setupOrientationMatrix(matrix, x, y, z);
      matrix.multiply(mc.gameRenderer.getCamera().getRotation());
      matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(particle.rotate()));
      RenderLayer renderLayer = RENDER_LAYER_CACHE.computeIfAbsent(
              particle.type(),
              type -> {
                 Identifier texture = type.texture();
                 return RenderLayer.of(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", texture).build());
              });
      Entry entry = matrix.peek();
      Matrix4f matrix4f = entry.getPositionMatrix();
      Matrix3f normalMatrix = entry.getNormalMatrix();
      VertexConsumer buffer = immediate.getBuffer(renderLayer);
      this.drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos, -pos, pos * 2.0F, pos * 2.0F, color, alpha);
      if (particle.type == ParticleType.BLOOM) {
         this.drawTexturedQuad(buffer, matrix4f, normalMatrix, -pos / 2.0F, -pos / 2.0F, pos, pos, color, alpha);
      }

      if (glowEnabled.get()) {
         float radiusMul = glowRadius.get().floatValue();
         float intensity = glowIntensity.get().floatValue();
         float bloomPos = pos * radiusMul;
         int bloomAlpha = (int) (alpha * 0.42F * intensity);
         if (bloomAlpha > 0) {
            if (cachedBloomLayer == null) {
               cachedBloomLayer = RenderLayer.of(BLOOM_TEXTURE.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", BLOOM_TEXTURE).build());
            }
            VertexConsumer bloomBuffer = immediate.getBuffer(cachedBloomLayer);
            this.drawTexturedQuad(bloomBuffer, matrix4f, normalMatrix, -bloomPos, -bloomPos, bloomPos * 2.0F, bloomPos * 2.0F, color, bloomAlpha);
         }
         if (glowLighting.get()) {
            float lightPos = pos * radiusMul * 1.75F;
            int lightAlpha = (int) (alpha * 0.22F * intensity);
            if (lightAlpha > 0) {
               if (cachedBloomSoftLayer == null) {
                  cachedBloomSoftLayer = RenderLayer.of(BLOOM_SOFT_TEXTURE.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", BLOOM_SOFT_TEXTURE).build());
               }
               VertexConsumer softBuffer = immediate.getBuffer(cachedBloomSoftLayer);
               this.drawTexturedQuad(softBuffer, matrix4f, normalMatrix, -lightPos, -lightPos, lightPos * 2.0F, lightPos * 2.0F, color, lightAlpha);
            }
         }
      }

      matrix.pop();
   }

   private void renderVegaParticles(MatrixStack matrix, Immediate immediate, Vec3d cameraPos, double deltaTime) {
      if (this.vegaParticles.isEmpty()) {
         return;
      }
      matrix.push();

      boolean colorize = particleMode2.is("Star");
      float i = 0.0F;
      int totalCount = this.vegaParticles.size();
      for (VegaParticle part : this.vegaParticles) {
         if (part == null || part.toRemove) continue;

         int color;
         if (part.color >= 0) {
            color = part.color;
         } else if (colorize) {
            int c1 = ColorUtil.fade(10, (int)(i * 5.0F), ColorUtil.fade(), ColorUtil.multDark(ColorUtil.fade(), 0.5F));
            int c2 = ColorUtil.fade(10, (int)(i * 5.0F) + 120, ColorUtil.fade(), ColorUtil.multDark(ColorUtil.fade(), 0.5F));
            color = ColorUtil.getOverallColorFrom(c1, c2, Mathf.clamp(0.0F, 1.0F, i / Math.max(1, totalCount)));
         } else {
            color = ColorUtil.getColor(160);
         }

         float alphaAnim = part.alphaPC;
         int alpha = (int)(alphaAnim * 255.0F);
         if (alpha > 5) {
            int finalColor = ColorUtil.replAlpha(color, alpha);
            this.renderVegaParticle(matrix, immediate, part, finalColor, alpha);
         }
         i += 1.0F;
      }

      matrix.pop();
   }

   private void renderVegaParticle(MatrixStack matrix, Immediate immediate, VegaParticle part, int color, int alpha) {
      float pSize = (float)(0.1 + size.get() * 0.4);
      matrix.push();

      Renderer2D.setupOrientationMatrix(matrix, (float) part.posX, (float) part.posY + pSize * 0.5F, (float) part.posZ);

      if (part.lower) {
         float partialTicks = mc.getRenderTickCounter().getDynamicDeltaTicks();
         float rotY = (float) Mathf.lerp(part.prevRotY, part.rotY, partialTicks);
         float rotX = (float) Mathf.lerp(part.prevRotX, part.rotX, partialTicks);
         matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotY));
         matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
         if (!particleMode2.is("Bitcoin")) {
            matrix.scale(2.0F, 2.0F, 2.0F);
         }
      } else {
         matrix.multiply(mc.gameRenderer.getCamera().getRotation());
      }

      matrix.scale(-pSize, -pSize, -pSize);
      float spinAngle = part.time.elapsedTime() / (float) part.maxTime * 1200.0F * (part.motionXSign > 0.0F ? 1.0F : -1.0F);
      matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(spinAngle));

      ParticleType vegaType = switch (particleMode2.get()) {
         case "Bitcoin" -> ParticleType.VEGA_BITCOIN;
         case "Star" -> ParticleType.VEGA_STAR;
         default -> ParticleType.VEGA_DOLLAR;
      };
      RenderLayer renderLayer = RENDER_LAYER_CACHE.computeIfAbsent(
              vegaType,
              type -> {
                 Identifier texture = type.texture();
                 return RenderLayer.of(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", texture).build());
              });

      Entry entry = matrix.peek();
      Matrix4f matrix4f = entry.getPositionMatrix();
      Matrix3f normalMatrix = entry.getNormalMatrix();
      VertexConsumer buffer = immediate.getBuffer(renderLayer);
      this.drawTexturedQuad(buffer, matrix4f, normalMatrix, 0.0F, 0.0F, 1.0F, 1.0F, color, alpha);

      if (glowEnabled.get()) {
         float radiusMul = glowRadius.get().floatValue() * 0.5F;
         float intensity = glowIntensity.get().floatValue();
         int bloomAlpha = (int) (alpha * 0.42F * intensity);
         if (bloomAlpha > 0) {
            if (cachedBloomLayer == null) {
               cachedBloomLayer = RenderLayer.of(BLOOM_TEXTURE.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", BLOOM_TEXTURE).build());
            }
            VertexConsumer bloomBuffer = immediate.getBuffer(cachedBloomLayer);
            float bloomExt = radiusMul;
            float x1 = 0.5F - bloomExt;
            float y1 = 0.5F - bloomExt;
            float bw = 2.0F * bloomExt;
            this.drawTexturedQuad(bloomBuffer, matrix4f, normalMatrix, x1, y1, bw, bw, color, bloomAlpha);
         }
         if (glowLighting.get()) {
            float lightExt = radiusMul * 1.75F;
            int lightAlpha = (int) (alpha * 0.22F * intensity);
            if (lightAlpha > 0) {
               if (cachedBloomSoftLayer == null) {
                  cachedBloomSoftLayer = RenderLayer.of(BLOOM_SOFT_TEXTURE.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", BLOOM_SOFT_TEXTURE).build());
               }
               VertexConsumer softBuffer = immediate.getBuffer(cachedBloomSoftLayer);
               float x1 = 0.5F - lightExt;
               float y1 = 0.5F - lightExt;
               float bw = 2.0F * lightExt;
               this.drawTexturedQuad(softBuffer, matrix4f, normalMatrix, x1, y1, bw, bw, color, lightAlpha);
            }
         }
      }

      matrix.pop();
   }

   private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normalMatrix, float x, float y,
                                 float width, float height, int color, int alpha) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      REUSABLE_NORMAL.set(0.0F, 0.0F, 1.0F);
      normalMatrix.transform(REUSABLE_NORMAL);
      REUSABLE_NORMAL.normalize();
      float x2 = x + width;
      float y2 = y + height;
      buffer.vertex(matrix, x, y, 0.0F)
              .color(r, g, b, alpha)
              .texture(0.0F, 1.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
      buffer.vertex(matrix, x2, y, 0.0F)
              .color(r, g, b, alpha)
              .texture(1.0F, 1.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
      buffer.vertex(matrix, x2, y2, 0.0F)
              .color(r, g, b, alpha)
              .texture(1.0F, 0.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
      buffer.vertex(matrix, x, y2, 0.0F)
              .color(r, g, b, alpha)
              .texture(0.0F, 0.0F)
              .overlay(OverlayTexture.DEFAULT_UV)
              .light(15728880)
              .normal(REUSABLE_NORMAL.x, REUSABLE_NORMAL.y, REUSABLE_NORMAL.z);
   }

   @Override
   public void toggle() {
      super.toggle();
      this.clear();
   }

   @Environment(EnvType.CLIENT)
   public static class Particle {
      private Box box;
      private final ParticleType type;
      private double posX, posY, posZ;
      private double velX, velY, velZ;
      private final int index;
      private final int rotate;
      private final int color;
      private final float size;
      private static final double BASE_VELOCITY = 0.05;
      private final double speedMultiplier;
      private final StopWatch time = new StopWatch();
      private final Animation animation = new Animation();

      public Particle(ParticleType type, Vec3d position, Vec3d velocity, int index, int rotate, int color,
                      float size, double speedMultiplier) {
         double halfSize = size / 2.0;
         this.box = new Box(
                 new Vec3d(position.x - halfSize, position.y - halfSize, position.z - halfSize),
                 new Vec3d(position.x + halfSize, position.y + halfSize, position.z + halfSize));
         this.type = type;
         this.posX = position.x;
         this.posY = position.y;
         this.posZ = position.z;
         double vm = 0.05;
         this.velX = velocity.x * vm;
         this.velY = velocity.y * vm;
         this.velZ = velocity.z * vm;
         this.index = index;
         this.rotate = rotate;
         this.color = color;
         this.size = size;
         this.speedMultiplier = speedMultiplier;
         this.time.reset();
      }

      public void update(boolean physic, double deltaTime) {
         if (physic && Module.mc.world != null) {
            double velMagSq = this.velX * this.velX
                    + this.velY * this.velY
                    + this.velZ * this.velZ;
            if (velMagSq > 1.0E-4) {
               if (PlayerUtil.isBlockSolid(this.posX, this.posY, this.posZ + this.velZ)) {
                  this.velX *= 1.35F; this.velY *= 1.35F; this.velZ *= -1.1;
               }

               if (PlayerUtil.isBlockSolid(this.posX, this.posY + this.velY, this.posZ)) {
                  this.velX *= 1.35F; this.velY *= -1.1; this.velZ *= 1.35F;
               }

               if (PlayerUtil.isBlockSolid(this.posX + this.velX, this.posY, this.posZ)) {
                  this.velX *= -1.1; this.velY *= 1.35F; this.velZ *= 1.35F;
               }
            }

            double friction = Math.pow(0.999, deltaTime * 60.0);
            this.velX = this.velX * friction;
            this.velY = this.velY * friction - 2.0E-5;
            this.velZ = this.velZ * friction;
         }

         double deltaMultiplier = deltaTime * 60.0 * this.speedMultiplier;
         this.posX += this.velX * deltaMultiplier;
         this.posY += this.velY * deltaMultiplier;
         this.posZ += this.velZ * deltaMultiplier;
         this.box = null;
      }

      @Generated
      public Box box() {
         if (this.box == null) {
            double halfSize = this.size / 2.0;
            this.box = new Box(
                    new Vec3d(this.posX - halfSize, this.posY - halfSize, this.posZ - halfSize),
                    new Vec3d(this.posX + halfSize, this.posY + halfSize, this.posZ + halfSize));
         }
         return this.box;
      }

      @Generated
      public ParticleType type() {
         return this.type;
      }

      @Generated
      public Vec3d position() {
         return new Vec3d(this.posX, this.posY, this.posZ);
      }

      @Generated
      public Vec3d velocity() {
         return new Vec3d(this.velX, this.velY, this.velZ);
      }

      @Generated
      public int index() {
         return this.index;
      }

      @Generated
      public int rotate() {
         return this.rotate;
      }

      @Generated
      public int color() {
         return this.color;
      }

      @Generated
      public float size() {
         return this.size;
      }

      @Generated
      public double speedMultiplier() {
         return this.speedMultiplier;
      }

      @Generated
      public StopWatch time() {
         return this.time;
      }

      @Generated
      public Animation animation() {
         return this.animation;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class VegaParticle {
      private static final float ALPHA_SPEED = 0.035F;
      public boolean toRemove = false;
      public float alphaPC = 0.0F;
      public double posX, posY, posZ;

      private float motionX;
      private float motionY;
      private float motionZ;
      public final float motionXSign;
      private final float speed;

      private final StopWatch time = new StopWatch();
      public final long maxTime;
      public final boolean lower;

      private float rotY, rotX;
      private float rotSpeedY, rotSpeedX;
      private float prevRotY, prevRotX;

      public int color = -1;

      public VegaParticle(Vec3d vec, boolean lower, long maxTime, float speed) {
         this.posX = vec.x;
         this.posY = vec.y;
         this.posZ = vec.z;
         this.speed = Math.max(0.01F, speed);

         float baseMotion = 0.15F * this.speed;
         this.motionX = Mathf.randomValue(-baseMotion, baseMotion);
         this.motionY = Mathf.randomValue(-baseMotion * 0.3F, baseMotion * 0.4F);
         this.motionZ = Mathf.randomValue(-baseMotion, baseMotion);
         this.motionXSign = this.motionX;
         this.maxTime = maxTime;
         this.lower = lower;
         this.time.reset();

         if (this.lower) {
            this.rotY = -360.0F + 720.0F * (float) Math.random();
            this.rotX = -180.0F + 360.0F * (float) Math.random();
            this.rotSpeedY = this.rotY / 10.0F;
            this.rotSpeedX = this.rotX / 10.0F;
            this.prevRotY = this.rotY;
            this.prevRotX = this.rotX;
         }
      }

      public void updatePart() {
         long elapsed = this.time.elapsedTime();
         if (elapsed > this.maxTime) {
            this.alphaPC = Math.max(0.0F, this.alphaPC - ALPHA_SPEED);
         } else {
            this.alphaPC = Math.min(1.0F, this.alphaPC + ALPHA_SPEED);
         }
         if (this.alphaPC < 0.005F && elapsed > this.maxTime) {
            this.toRemove = true;
         }
         if (this.toRemove) {
            return;
         }
         this.gravityAndMove();
      }

      private void gravityAndMove() {
         if (this.lower) {
            this.prevRotY = this.rotY;
            this.prevRotX = this.rotX;
            this.rotY = this.rotY + this.rotSpeedY;
            this.rotX = this.rotX + this.rotSpeedX;
            this.rotSpeedY = this.rotSpeedY * 0.96F;
            this.rotSpeedX = this.rotSpeedX * 0.96F;
         }

         if (mc.world != null) {
            BlockPos xPrePos = BlockPos.ofFloored(this.posX + this.motionX * 2.0F, this.posY - this.motionY + 0.1, this.posZ);
            BlockPos yPrePos = BlockPos.ofFloored(this.posX, this.posY + this.motionY * 2.0F, this.posZ);
            BlockPos zPrePos = BlockPos.ofFloored(this.posX, this.posY - this.motionY + 0.1, this.posZ + this.motionZ * 2.0F);
            boolean collideX = !mc.world.isAir(xPrePos);
            boolean collideY = !mc.world.isAir(yPrePos);
            boolean collideZ = !mc.world.isAir(zPrePos);

            if (this.motionY != 0.0F) {
               this.motionY -= this.lower ? 0.003333F : 0.01F;
            }

            if (collideY) {
               this.motionY = -this.motionY * (this.lower ? 0.7F : 0.9F);
            }
            if (collideX) {
               this.motionX *= this.lower ? -0.6F : -1.0F;
            }
            if (collideZ) {
               this.motionZ *= this.lower ? -0.6F : -1.0F;
            }
         }

         this.posX += this.motionX;
         this.posY += this.motionY;
         this.posZ += this.motionZ;
      }
   }

   @Environment(EnvType.CLIENT)
   static enum ParticleType {
      HEART("heart", true),
      STAR("star", true),
      SNOW("snowflake", true),
      BLOOM("firefly", true),
      DOLLAR("dollar", true),
      TRIANGLE("triangle", true),
      SAKURA("sakura", true),
      GEMINI("genshin", true),
      SIMS("rhombus", true),
      VEGA_DOLLAR("vega_dollar", true),
      VEGA_BITCOIN("vega_bitcoin", true),
      VEGA_STAR("vega_star", true);

      private final Identifier texture;
      private final boolean rotatable;

      private ParticleType(String name, boolean rotatable) {
         this.texture = Identifier.of("vesence", "textures/world/" + name + ".png");
         this.rotatable = rotatable;
      }

      @Generated
      public Identifier texture() {
         return this.texture;
      }

      @Generated
      public boolean rotatable() {
         return this.rotatable;
      }
   }
}
