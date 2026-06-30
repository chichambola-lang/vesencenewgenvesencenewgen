package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@IModule(name = "DashTrails", description = "Текстурированный трейл за игроком с эффектами", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class DashTrails extends Module {

    public final BooleanSetting self = new BooleanSetting("Свой трейл", true);
    public final BooleanSetting players = new BooleanSetting("Игроки", false);
    public final BooleanSetting friends = new BooleanSetting("Друзья", true);

    private final ModeSetting colorMode = new ModeSetting("Цвет", "RandomPalette",
            "RandomPalette", "Client", "Rainbow");
    private final SliderSetting maxDist = new SliderSetting("Макс. дистанция", 25f, 5f, 100f, 1f);
    private final BooleanSetting motionSmooth = new BooleanSetting("Сглаживание движения", false);
    private final BooleanSetting dashSegments = new BooleanSetting("Сегменты", false);
    private final BooleanSetting dashDots = new BooleanSetting("Точки", true);
    private final BooleanSetting lighting = new BooleanSetting("Подсветка", true);
    private final SliderSetting dashLength = new SliderSetting("Длина", 0.75f, 0.5f, 1.5f, 0.05f);
    private final SliderSetting glowIntensity = new SliderSetting("Свечение", 0.35f, 0f, 1f, 0.05f);
    private final BooleanSetting gravity1 = new BooleanSetting("Гравитация частиц", true);
    private final SliderSetting friction = new SliderSetting("Трение воздуха", 0.92f, 0.5f, 0.99f, 0.01f);

    private static final String TEX_ROOT = "textures/dashtrail/";
    private static final String TEX_CUBICS = TEX_ROOT + "dashcubics/";
    private static final String TEX_GROUPS = TEX_CUBICS + "group_dashs/";
    private static final int TEX_COUNT = 21;
    private static final int[] GROUP_SIZES = {11, 23, 32, 16, 32};

    private static final Identifier BLOOM_TEX = Identifier.of("vesence", TEX_ROOT + "dashbloomsample.png");
    private final List<ResWithSize> cubicTextures = new ArrayList<>();
    private final List<List<ResWithSize>> animatedTextures = new ArrayList<>();

    private final Random random = new Random(1234567891L);

    private boolean isRandomPalette, isClient;
    private int rainbowColor = -1;

    private final List<DashCubic> cubics = new ArrayList<>(256);
    private boolean texturesLoaded = false;

    private double selfPrevX = Double.MAX_VALUE;
    private double selfPrevY = Double.MAX_VALUE;
    private double selfPrevZ = Double.MAX_VALUE;

    private final Map<Integer, double[]> entityPrevPos = new HashMap<>();
    private final Map<Integer, Float> entityPrevYaw = new HashMap<>();

    private static final RenderPipeline TEX_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/dashtrail_tex"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderPipeline BLOOM_QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/dashtrail_bloom"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderPipeline COLOR_QUADS_ADD_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/dashtrail_color_add"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer BLOOM_LAYER = RenderLayer.of(
            "vesence_dashtrail_bloom",
            RenderSetup.builder(BLOOM_QUADS_PIPELINE)
                    .expectedBufferSize(4096)
                    .translucent()
                    .texture("Sampler0", BLOOM_TEX)
                    .build());

    private static final RenderLayer COLOR_ADD_LAYER = RenderLayer.of(
            "vesence_dashtrail_color_add",
            RenderSetup.builder(COLOR_QUADS_ADD_PIPELINE)
                    .expectedBufferSize(16384)
                    .translucent()
                    .build());

    private final IdentityHashMap<Identifier, RenderLayer> textureLayers = new IdentityHashMap<>();

    private BufferAllocator allocator;
    private Immediate immediate;

    private long frameTime;

    private final List<DashCubic> filteredPool = new ArrayList<>(256);

    public DashTrails() {
        addSettings(self, players, friends, colorMode,
                maxDist, motionSmooth, dashSegments, dashDots, lighting, dashLength, glowIntensity,
                gravity1, friction);
    }

    private RenderLayer getTextureLayer(Identifier id) {
        RenderLayer layer = textureLayers.get(id);
        if (layer != null) return layer;
        layer = RenderLayer.of(
                "vesence_dashtrail_" + id.getPath().replace('/', '_').replace('.', '_'),
                RenderSetup.builder(TEX_QUADS_PIPELINE)
                        .expectedBufferSize(4096)
                        .translucent()
                        .texture("Sampler0", id)
                        .build()
        );
        textureLayers.put(id, layer);
        return layer;
    }

    private void loadTextures() {
        if (texturesLoaded) return;

        cubicTextures.clear();
        for (int i = 1; i <= TEX_COUNT; i++) {
            Identifier id = Identifier.of("vesence", TEX_CUBICS + "dashcubic" + i + ".png");
            try {
                mc.getResourceManager().getResource(id).orElseThrow();
                cubicTextures.add(new ResWithSize(id));
            } catch (Exception ignored) {
            }
        }

        animatedTextures.clear();
        for (int g = 0; g < GROUP_SIZES.length; g++) {
            List<ResWithSize> group = new ArrayList<>();
            for (int f = 1; f <= GROUP_SIZES[g]; f++) {
                Identifier id = Identifier.of("vesence", TEX_GROUPS + "group" + (g + 1) + "/dashcubic" + f + ".png");
                try {
                    mc.getResourceManager().getResource(id).orElseThrow();
                    group.add(new ResWithSize(id));
                } catch (Exception ignored) {
                }
            }
            if (!group.isEmpty()) animatedTextures.add(group);
        }

        texturesLoaded = !cubicTextures.isEmpty();
    }

    private int[] getResolution(Identifier id) {
        try (InputStream is = mc.getResourceManager().getResource(id).orElseThrow().getInputStream()) {
            BufferedImage img = ImageIO.read(is);
            return new int[]{img.getWidth(), img.getHeight()};
        } catch (Exception e) {
            return new int[]{32, 32};
        }
    }

    private int getCubicColor() {
        if (isRandomPalette) return Color.getHSBColor(random.nextFloat(), 1f, 1f).getRGB();
        if (isClient) return ColorUtil.fade();
        return rainbowColor;
    }

    private static int swapAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, (int) alpha));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private static int mixToward(int c1, int c2, float t) {
        int r = (int) ((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
        int g = (int) ((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        int a = (int) ((c1 >> 24 & 0xFF) + ((c2 >> 24 & 0xFF) - (c1 >> 24 & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int toDark(int color, float f) {
        return swapAlpha(color, (color >> 24 & 0xFF) * f);
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static double fadeEasing(double t) {
        t = Math.max(0, Math.min(1, t));
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (!this.enable) return;
        if (mc.world == null || mc.player == null) return;

        frameTime = System.currentTimeMillis();

        loadTextures();
        updateCubics();

        if (!texturesLoaded) return;

        isRandomPalette = colorMode.is("RandomPalette");
        isClient = colorMode.is("Client");
        if (!isRandomPalette && !isClient) {
            rainbowColor = Color.getHSBColor((frameTime % 1000L) / 1000f, 0.8f, 1f).getRGB();
        }

        float pt = event.getTickDelta();
        float aPC = 1f;
        float glowMul = glowIntensity.get().floatValue();

        filteredPool.clear();
        getFiltered(filteredPool);
        if (filteredPool.isEmpty()) return;

        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        double cx = cam.x, cy = cam.y, cz = cam.z;
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();
        float cameraPitch = mc.gameRenderer.getCamera().getPitch();

        boolean dots = dashDots.get();
        boolean segments = dashSegments.get();
        MatrixStack matrices = event.getMatrixStack();

        if (allocator == null) {
            allocator = new BufferAllocator(1048576);
            immediate = VertexConsumerProvider.immediate(allocator);
        }

        try {

            if (dots || segments) {
                renderSparks(matrices, immediate, filteredPool, pt, cx, cy, cz, cameraYaw, cameraPitch, dots, segments);
            }

            float lightPC = lighting.get() ? 1f : 0f;
            for (DashCubic dc : filteredPool) {
                dc.draw(matrices, immediate, pt, false, aPC, lightPC, cx, cy, cz, cameraYaw, cameraPitch, glowMul);
            }
            for (DashCubic dc : filteredPool) {
                dc.draw(matrices, immediate, pt, true, aPC, lightPC, cx, cy, cz, cameraYaw, cameraPitch, glowMul);
            }

            immediate.draw();
        } catch (Exception e) {
            e.printStackTrace();
            resetBuffer();
        }
    }

    private void resetBuffer() {
        if (allocator != null) {
            try {
                allocator.close();
            } catch (Exception ignored) {
            }
        }
        allocator = null;
        immediate = null;
    }

    private void renderSparks(MatrixStack matrices, Immediate immediate, List<DashCubic> vis,
                              float pt, double cx, double cy, double cz,
                              float cameraYaw, float cameraPitch, boolean dots, boolean segments) {
        for (DashCubic dc : vis) {
            double dx = dc.getRenderX(pt) - cx;
            double dy = dc.getRenderY(pt) - cy;
            double dz = dc.getRenderZ(pt) - cz;

            for (DashSpark spark : dc.sparks) {
                float timePc = spark.timePC(frameTime);
                if (timePc >= 1f) continue;

                float sAPC = (float) smoothstep(spark.alphaPC(frameTime) * dc.alphaCur);

                if (dots) {
                    int c = mixToward(dc.color, swapAlpha(-1, (dc.color >> 24 & 0xFF)), sAPC);
                    c = swapAlpha(c, (c >> 24 & 0xFF) * sAPC / 1.333f);
                    renderSparkQuad(matrices, immediate, spark, pt, dx, dy, dz, cameraYaw, cameraPitch, c, 0.021f);
                }

                if (segments) {
                    int c = mixToward(dc.color, swapAlpha(-1, (dc.color >> 24 & 0xFF)), 1f - sAPC);
                    c = swapAlpha(c, (c >> 24 & 0xFF) * sAPC / 3f);
                    renderSparkQuad(matrices, immediate, spark, pt, dx, dy, dz, cameraYaw, cameraPitch, c, 0.025f);
                }
            }
        }
    }

    private void renderSparkQuad(MatrixStack matrices, Immediate immediate, DashSpark spark,
                                 float pt, double dx, double dy, double dz,
                                 float cameraYaw, float cameraPitch, int color, float scale) {
        matrices.push();
        matrices.translate(spark.getRX(pt) + dx, spark.getRY(pt) + dy, spark.getRZ(pt) + dz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));
        matrices.scale(scale, scale, scale);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        VertexConsumer buffer = immediate.getBuffer(COLOR_ADD_LAYER);
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = (color >> 24) & 0xFF;
        addColorQuad(buffer, matrix, r, g, b, a);
        matrices.pop();
    }

    private static void addColorQuad(VertexConsumer buffer, Matrix4f matrix, int r, int g, int b, int a) {
        buffer.vertex(matrix, -0.5f, -0.5f, 0).color(r, g, b, a);
        buffer.vertex(matrix, -0.5f, 0.5f, 0).color(r, g, b, a);
        buffer.vertex(matrix, 0.5f, 0.5f, 0).color(r, g, b, a);
        buffer.vertex(matrix, 0.5f, -0.5f, 0).color(r, g, b, a);
    }

    private void updateCubics() {
        if (mc.player == null || mc.world == null) return;

        for (int i = cubics.size() - 1; i >= 0; i--) {
            DashCubic c = cubics.get(i);
            float tpc = c.getTimePC(frameTime);
            if (tpc >= 1f && c.alphaTo != 0f) c.alphaTo = 0f;
            if (tpc >= 1f && c.alphaTo == 0f && c.alphaCur < 0.02f) {
                cubics.remove(i);
            }
        }

        List<DashCubic> all = cubics;
        int size = all.size();
        int max = motionSmooth.get() ? size - 1 : -1;
        for (int i = 0; i < size; i++) {
            DashCubic next = (i < max) ? all.get(i + 1) : null;
            all.get(i).motionProcess(next, frameTime);
        }

        if (self.get()) {
            if (selfPrevX == Double.MAX_VALUE) {
                selfPrevX = mc.player.getX();
                selfPrevY = mc.player.getY();
                selfPrevZ = mc.player.getZ();
            } else {
                trySpawn(mc.player, selfPrevX, selfPrevY, selfPrevZ,
                        entityPrevYaw.getOrDefault(mc.player.getId(), mc.player.getYaw()));
                selfPrevX = mc.player.getX();
                selfPrevY = mc.player.getY();
                selfPrevZ = mc.player.getZ();
                entityPrevYaw.put(mc.player.getId(), mc.player.getYaw());
            }
        }

        if (players.get() || friends.get()) {
            float maxDistSq = maxDist.get().floatValue();
            maxDistSq *= maxDistSq;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player) continue;
                boolean isFriend = FriendStorage.isFriend(p.getName().getString());
                if (!players.get() && !isFriend) continue;
                if (!friends.get() && isFriend) continue;
                if (mc.player.squaredDistanceTo(p) > maxDistSq) continue;

                double[] prev = entityPrevPos.computeIfAbsent(p.getId(), k -> new double[]{
                        p.lastRenderX, p.lastRenderY, p.lastRenderZ
                });
                float prevYaw = entityPrevYaw.getOrDefault(p.getId(), p.getYaw());
                trySpawn(p, prev[0], prev[1], prev[2], prevYaw);
                prev[0] = p.getX();
                prev[1] = p.getY();
                prev[2] = p.getZ();
                entityPrevYaw.put(p.getId(), p.getYaw());
            }
        }
    }

    private void trySpawn(PlayerEntity entity, double prevX, double prevY, double prevZ, float prevYaw) {
        double dx = entity.getX() - prevX;
        double dy = entity.getY() - prevY;
        double dz = entity.getZ() - prevZ;
        double speedXZ = Math.sqrt(dx * dx + dz * dz);
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speedXZ < 0.04 && speed < 0.06) return;

        boolean addDops = dashSegments.get() || dashDots.get();
        int countMax = MathHelper.clamp((int) (Math.max(speed, speedXZ) / 0.05), 1, 24);

        double motionX = entity.getX() - prevX;
        double motionY = entity.getY() - prevY;
        double motionZ = entity.getZ() - prevZ;

        for (int i = 0; i < countMax; i++) {
            float t = (float) i / countMax;

            float bezierT = t * t * (3f - 2f * t);

            cubics.add(new DashCubic(
                    new DashBase(entity, prevX, prevY, prevZ, motionX, motionY, motionZ, 0.04f,
                            new DashTexture(true), bezierT, getRandomTime(), prevYaw),
                    addDops));
        }
    }

    private int getRandomTime() {
        return (int) ((550 + random.nextInt(300)) * dashLength.get().floatValue());
    }

    private void getFiltered(List<DashCubic> out) {
        Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
        double camX = cam.x, camY = cam.y, camZ = cam.z;
        for (DashCubic c : cubics) {
            if (c == null) continue;
            if (c.alphaCur <= 0.005f && c.alphaTo <= 0f) continue;
            double ddx = c.base.posX - camX;
            double ddy = c.base.posY - camY;
            double ddz = c.base.posZ - camZ;
            double distSq = ddx * ddx + ddy * ddy + ddz * ddz;
            if (distSq > 0.25 && distSq < 9216) out.add(c);
        }
    }

    @Override
    public void onDisable() {
        cubics.clear();
        entityPrevPos.clear();
        entityPrevYaw.clear();
        selfPrevX = Double.MAX_VALUE;
        selfPrevY = Double.MAX_VALUE;
        selfPrevZ = Double.MAX_VALUE;
        textureLayers.clear();
        resetBuffer();
        super.onDisable();
    }

    private class ResWithSize {
        final Identifier source;
        final int[] res;

        ResWithSize(Identifier id) {
            this.source = id;
            this.res = getResolution(id);
        }
    }

    private class DashCubic {
        float alphaCur = 0.01f;
        float alphaTo = 1f;
        private static final float ALPHA_SPEED = 0.12f;

        final long startTime;
        final DashBase base;
        final int color;
        final float[] rotate = {0f, 0f};
        final List<DashSpark> sparks = new ArrayList<>();
        final boolean addDops;

        DashCubic(DashBase base, boolean addDops) {
            this.startTime = System.currentTimeMillis();
            this.base = base;
            this.addDops = addDops;
            this.color = getCubicColor();

            computeRotation();
        }

        private void computeRotation() {
            double mxz = Math.sqrt(base.motionX * base.motionX + base.motionZ * base.motionZ);
            if (mxz < 5e-4) {
                rotate[0] = (float) (360 * Math.random());
                rotate[1] = mc.gameRenderer.getCamera().getPitch();
            } else {
                float my = base.getMotionYaw();
                rotate[0] = my - 60f - (base.prevRotationYaw - base.rotationYaw) * 3f;
                float yd = Math.abs(MathHelper.wrapDegrees(my + 26.3f - base.rotationYaw));
                rotate[1] = (yd < 10f || yd > 160f) ? -90f : mc.gameRenderer.getCamera().getPitch();
            }
        }

        float getTimePC(long now) {
            return (now - startTime) / (float) base.rMTime;
        }

        double getRenderX(float pt) {
            return base.prevX + (base.posX - base.prevX) * pt;
        }

        double getRenderY(float pt) {
            return base.prevY + (base.posY - base.prevY) * pt;
        }

        double getRenderZ(float pt) {
            return base.prevZ + (base.posZ - base.prevZ) * pt;
        }

        void motionProcess(DashCubic next, long now) {

            alphaCur += (alphaTo - alphaCur) * ALPHA_SPEED;

            base.prevX = base.posX;
            base.prevY = base.posY;
            base.prevZ = base.posZ;

            double spd = Math.sqrt(base.motionX * base.motionX + base.motionY * base.motionY + base.motionZ * base.motionZ);

            if (spd < 0.01) {
                float friction = 0.95f;
                base.motionX *= friction;
                base.posX += base.motionX;
                base.motionY *= friction;
                base.posY += base.motionY / (base.motionY < 0 ? 1.0 : 3.5);
                base.motionZ *= friction;
                base.posZ += base.motionZ;
            } else {
                base.motionX /= 1.05;
                base.motionY /= 1.05;
                base.motionZ /= 1.05;
            }

            computeRotation();

            if (addDops && getTimePC(now) < 0.3f && random.nextInt(12) > 5) {
                int count = dashSegments.get() ? 1 : 2;
                for (int i = 0; i < count; i++) sparks.add(new DashSpark());
                sparks.forEach(DashSpark::move);
            }
            sparks.removeIf(s -> s.timePC(now) >= 1.0);
        }

        void draw(MatrixStack matrices, Immediate immediate, float pt, boolean bloom, float aPC,
                  float lightPC, double cx, double cy, double cz, float cameraYaw, float cameraPitch, float glowMul) {
            ResWithSize tex = base.texture.get(frameTime);
            if (tex == null) return;

            float a = alphaCur * aPC;
            if (a < 0.001f) return;

            float scale = 0.033f * a;
            float extX = tex.res[0] * scale;
            float extY = tex.res[1] * scale;

            double renderX = getRenderX(pt) - cx;
            double renderY = getRenderY(pt) - cy;
            double renderZ = getRenderZ(pt) - cz;

            matrices.push();
            matrices.translate(renderX, renderY, renderZ);

            if (bloom) {
                drawBloom(matrices, immediate, pt, a, extX, extY, lightPC, cameraYaw, cameraPitch, glowMul);
            } else {
                drawMain(matrices, immediate, tex, a, extX, extY);
            }

            matrices.pop();
        }

        private void drawBloom(MatrixStack matrices, Immediate immediate, float pt, float a,
                               float extX, float extY, float lightPC, float cameraYaw, float cameraPitch, float glowMul) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cameraYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cameraPitch));

            float extXY = (float) Math.sqrt(extX * extX + extY * extY);
            float tOf = Math.min(1f, 1f - getTimePC(frameTime));

            matrices.push();
            float bloomScale = extXY / 1.75f * (0.5f + 0.5f * glowMul);
            matrices.scale(bloomScale, bloomScale, 1f);
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = immediate.getBuffer(BLOOM_LAYER);
            int col = mixToward(color, -1, 0.15f);
            int alpha = (int) (30f * a * glowMul);
            addTexturedQuad(buffer, matrix, col, alpha);
            matrices.pop();

            if (lightPC != 0f) {
                float am = a * lightPC * glowMul;
                float bigScale = extXY / 1.75f * (1f + 3f * tOf * am);
                matrices.push();
                matrices.scale(bigScale, bigScale, 1f);
                Matrix4f lightMatrix = matrices.peek().getPositionMatrix();
                int darkCol = toDark(col, am / 4f);
                int lightAlpha = (int) (50f * am);
                addTexturedQuad(buffer, lightMatrix, darkCol, lightAlpha);
                matrices.pop();
            }
        }

        private void drawMain(MatrixStack matrices, Immediate immediate, ResWithSize tex, float a, float extX, float extY) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rotate[0]));

            int pitchSign = mc.options.getPerspective().isFrontView() ? -1 : 1;
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotate[1] * pitchSign));
            matrices.scale(-0.1f, -0.1f, 0.1f);

            Matrix4f matrix = matrices.peek().getPositionMatrix();
            VertexConsumer buffer = immediate.getBuffer(getTextureLayer(tex.source));
            int col = toDark(mixToward(color, -1, 0.4f), a);
            int alpha = (col >> 24) & 0xFF;

            float x1 = -extX / 2f;
            float y1 = -extY / 2f;
            float x2 = extX / 2f;
            float y2 = extY / 2f;

            int r = (col >> 16) & 0xFF, g = (col >> 8) & 0xFF, b = col & 0xFF;

            buffer.vertex(matrix, x1, y1, 0.0f).texture(0.0f, 1.0f).color(r, g, b, alpha)
                    .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
            buffer.vertex(matrix, x1, y2, 0.0f).texture(0.0f, 0.0f).color(r, g, b, alpha)
                    .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
            buffer.vertex(matrix, x2, y2, 0.0f).texture(1.0f, 0.0f).color(r, g, b, alpha)
                    .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
            buffer.vertex(matrix, x2, y1, 0.0f).texture(1.0f, 1.0f).color(r, g, b, alpha)
                    .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
        }
    }

    private static void addTexturedQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        buffer.vertex(matrix, -0.5f, -0.5f, 0.0f).texture(0.0f, 1.0f).color(r, g, b, alpha)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(matrix, 0.5f, -0.5f, 0.0f).texture(1.0f, 1.0f).color(r, g, b, alpha)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(matrix, 0.5f, 0.5f, 0.0f).texture(1.0f, 0.0f).color(r, g, b, alpha)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
        buffer.vertex(matrix, -0.5f, 0.5f, 0.0f).texture(0.0f, 0.0f).color(r, g, b, alpha)
                .overlay(OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
    }

    private class DashBase {
        final PlayerEntity entity;
        double motionX, motionY, motionZ;
        double posX, posY, posZ;
        double prevX, prevY, prevZ;
        final float prevRotationYaw, rotationYaw;
        final int rMTime;
        final DashTexture texture;

        DashBase(PlayerEntity entity, double ePrevX, double ePrevY, double ePrevZ,
                 double motionX, double motionY, double motionZ,
                 float speed, DashTexture tex, float offsetPC, int rmTime, float prevYaw) {
            this.entity = entity;
            this.rMTime = rmTime;
            this.texture = tex;
            this.prevRotationYaw = prevYaw;
            this.rotationYaw = entity.getYaw();

            this.motionX = -motionX * speed;
            this.motionY = -motionY * speed;
            this.motionZ = -motionZ * speed;

            double speedLen = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
            if (speedLen < 1e-6) {
                this.posX = ePrevX + (-0.0875 + 0.175 * Math.random());
                this.posY = ePrevY + (entity.getHeight() / 3.0 + entity.getHeight() / 4.0 * Math.random() * 0.7);
                this.posZ = ePrevZ + (-0.0875 + 0.175 * Math.random());
            } else {
                this.posX = ePrevX - this.motionX / speed * offsetPC + (-0.0875 + 0.175 * Math.random());
                this.posY = ePrevY - this.motionY / speed * offsetPC + (entity.getHeight() / 3.0 + entity.getHeight() / 4.0 * Math.random() * 0.7);
                this.posZ = ePrevZ - this.motionZ / speed * offsetPC + (-0.0875 + 0.175 * Math.random());
            }
            this.prevX = posX;
            this.prevY = posY;
            this.prevZ = posZ;
        }

        float getMotionYaw() {
            int y = (int) (Math.toDegrees(Math.atan2(motionZ, motionX)) - 90);
            return y < 0 ? y + 360 : y;
        }
    }

    private class DashTexture {
        final List<ResWithSize> textures;
        final boolean animated;
        final long spawnTime = System.currentTimeMillis();
        final long animTime;

        DashTexture(boolean tryAnim) {
            this.animated = tryAnim && (random.nextInt(100) > 40) && !animatedTextures.isEmpty();
            if (animated && !animatedTextures.isEmpty()) {
                textures = animatedTextures.get(random.nextInt(animatedTextures.size()));
                animTime = getRandomTime();
            } else {
                textures = new ArrayList<>();
                if (!cubicTextures.isEmpty())
                    textures.add(cubicTextures.get(random.nextInt(cubicTextures.size())));
                animTime = 0;
            }
        }

        ResWithSize get(long now) {
            if (textures.isEmpty()) return null;
            if (!animated) return textures.get(0);
            float count = textures.size();
            float tPC = (float) ((now - spawnTime) % animTime) / animTime;
            int idx = MathHelper.clamp((int) (tPC * count), 0, (int) count - 1);
            return textures.get(idx);
        }
    }

    private class DashSpark {
        double posX, posY, posZ;
        double prevX, prevY, prevZ;
        double speed = Math.random() / 50.0;
        double yaw = Math.random() * 360;
        double pitch = -90 + Math.random() * 180;
        final long startTime = System.currentTimeMillis();

        double velocityX, velocityY, velocityZ;
        final double gravity = 0.0003;

        DashSpark() {

            double ry = Math.toRadians(yaw);
            double rp = Math.toRadians(pitch);
            velocityX = Math.sin(ry) * Math.cos(rp) * speed;
            velocityY = Math.sin(rp) * speed;
            velocityZ = Math.cos(ry) * Math.cos(rp) * speed;
        }

        float timePC(long now) {
            return Math.min(1f, (now - startTime) / 1000f);
        }

        double alphaPC(long now) {
            return 1.0 - timePC(now);
        }

        void move() {
            prevX = posX;
            prevY = posY;
            prevZ = posZ;

            if (gravity1.get()) {
                velocityY -= gravity;
            }

            float airFriction = friction.get().floatValue();
            velocityX *= airFriction;
            velocityY *= airFriction;
            velocityZ *= airFriction;

            posX += velocityX;
            posY += velocityY;
            posZ += velocityZ;

            speed *= 0.95;
        }

        double getRX(float pt) {
            return prevX + (posX - prevX) * pt;
        }

        double getRY(float pt) {
            return prevY + (posY - prevY) * pt;
        }

        double getRZ(float pt) {
            return prevZ + (posZ - prevZ) * pt;
        }
    }
}
