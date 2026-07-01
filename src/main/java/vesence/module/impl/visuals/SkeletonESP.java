package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.access.ILivingEntityRendererAccess;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.utils.render.ColorUtil;

/**
 * SkeletonESP — скелет игрока с настройкой X-Ray и свечением (bloom).
 * Каждая кость рисуется как линия + текстурированный «glow» биллборд вдоль
 * сегмента (через bloom.png), создавая эффект светящегося скелета.
 */
@IModule(name = "SkeletonESP", description = "Светящийся скелет игрока", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class SkeletonESP extends Module {

    private static final float MODEL_SCALE = 0.0625F;
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    private static final Identifier BLOOM_TEXTURE = Identifier.of("vesence", "textures/targetesp/bloom.png");

    private final BooleanSetting renderSelf = new BooleanSetting("Рендерить себя", false);

    // Пульсация bloom
    private static final float PULSE_SPEED = 3.5F; // скорость пульса (герц)
    private static final float PULSE_MIN = 0.4F;   // минимум яркости (0..1)
    private static final float PULSE_MAX = 1.0F;   // максимум яркости

    // ─── Линии (кости) ───────────────────────────────────────────────────────
    private static final RenderPipeline SKELETON_LINES_XRAY_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/skeleton_lines_xray"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderPipeline SKELETON_LINES_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/skeleton_lines_depth"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());

    private static final RenderLayer SKELETON_XRAY_LAYER = RenderLayer.of(
            "vesence_skeleton_xray",
            RenderSetup.builder(SKELETON_LINES_XRAY_PIPELINE).expectedBufferSize(4096).translucent().build());

    private static final RenderLayer SKELETON_DEPTH_LAYER = RenderLayer.of(
            "vesence_skeleton_depth",
            RenderSetup.builder(SKELETON_LINES_DEPTH_PIPELINE).expectedBufferSize(4096).translucent().build());

    // ─── Свечение (bloom billboards вдоль костей) ────────────────────────────
    private static final RenderPipeline SKELETON_GLOW_XRAY_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/skeleton_glow_xray"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderPipeline SKELETON_GLOW_DEPTH_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "pipeline/world/skeleton_glow_depth"))
                    .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderLayer SKELETON_GLOW_XRAY_LAYER = RenderLayer.of(
            "vesence_skeleton_glow_xray",
            RenderSetup.builder(SKELETON_GLOW_XRAY_PIPELINE).expectedBufferSize(16384).translucent()
                    .texture("Sampler0", BLOOM_TEXTURE).build());

    private static final RenderLayer SKELETON_GLOW_DEPTH_LAYER = RenderLayer.of(
            "vesence_skeleton_glow_depth",
            RenderSetup.builder(SKELETON_GLOW_DEPTH_PIPELINE).expectedBufferSize(16384).translucent()
                    .texture("Sampler0", BLOOM_TEXTURE).build());

    public SkeletonESP() {
        this.addSettings(new Setting[]{this.renderSelf});
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        // Пульсация: sin-волна по времени, модулирует альфу и ширину glow
        float pulse = PULSE_MIN + (PULSE_MAX - PULSE_MIN) *
                (0.5F + 0.5F * (float) Math.sin(System.currentTimeMillis() * 0.001 * PULSE_SPEED * Math.PI * 2.0));

        // Glow (рисуем первым — под линиями). Отдельный allocator/Immediate.
        BufferAllocator glowAlloc = new BufferAllocator(65536);
        VertexConsumerProvider.Immediate glowImm = VertexConsumerProvider.immediate(glowAlloc);
        try {
            VertexConsumer glowBuffer = glowImm.getBuffer(SKELETON_GLOW_XRAY_LAYER);
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player && !this.renderSelf.get()) continue;
                if (player.isInvisible()) continue;
                this.renderPlayerPass(event.getMatrixStack(), cameraPos, player, tickDelta, null, glowBuffer, pulse);
            }
            glowImm.draw();
        } finally {
            glowAlloc.close();
        }

        // Линии (поверх glow). Отдельный allocator/Immediate.
        BufferAllocator lineAlloc = new BufferAllocator(16384);
        VertexConsumerProvider.Immediate lineImm = VertexConsumerProvider.immediate(lineAlloc);
        try {
            VertexConsumer lineBuffer = lineImm.getBuffer(SKELETON_XRAY_LAYER);
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player && !this.renderSelf.get()) continue;
                if (player.isInvisible()) continue;
                this.renderPlayerPass(event.getMatrixStack(), cameraPos, player, tickDelta, lineBuffer, null, pulse);
            }
            lineImm.draw();
        } finally {
            lineAlloc.close();
        }
    }

    private void renderPlayerPass(MatrixStack matrices, Vec3d cameraPos,
                              PlayerEntity player, float tickDelta,
                              VertexConsumer lineBuffer, VertexConsumer glowBuffer, float pulse) {
        if (player == mc.player && mc.options.getPerspective() == Perspective.FIRST_PERSON) return;
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) return;

        EntityRenderer<?, ?> rawRenderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(rawRenderer instanceof PlayerEntityRenderer renderer)) return;

        PlayerEntityRenderState state = renderer.createRenderState();
        renderer.updateRenderState(clientPlayer, state, tickDelta);
        PlayerEntityModel model = (PlayerEntityModel) renderer.getModel();
        model.setAngles(state);

        matrices.push();
        this.setupModelMatrix(matrices, state, renderer, cameraPos, player, tickDelta);

        int color = ColorUtil.fade();
        if ((color >> 24 & 0xFF) == 0) color |= 0xFF000000;

        this.drawSkeleton(matrices, model, lineBuffer, glowBuffer, color, pulse);
        matrices.pop();
    }

    private void setupModelMatrix(MatrixStack matrices, PlayerEntityRenderState state,
                                  PlayerEntityRenderer renderer, Vec3d cameraPos,
                                  PlayerEntity player, float tickDelta) {
        Vec3d pos = player.getLerpedPos(tickDelta);
        matrices.translate(pos.x - cameraPos.x, pos.y - cameraPos.y, pos.z - cameraPos.z);
        if (state.sleepingDirection != null) {
            float eyeOffset = state.standingEyeHeight - 0.1F;
            matrices.translate(
                    -state.sleepingDirection.getOffsetX() * eyeOffset, 0.0F,
                    -state.sleepingDirection.getOffsetZ() * eyeOffset);
        }
        float baseScale = state.baseScale;
        matrices.scale(baseScale, baseScale, baseScale);
        ILivingEntityRendererAccess accessor = (ILivingEntityRendererAccess) renderer;
        accessor.vesence$invokeSetupTransforms(state, matrices, state.bodyYaw, baseScale);
        matrices.scale(-1.0F, -1.0F, 1.0F);
        accessor.vesence$invokeScale(state, matrices);
        matrices.translate(0.0F, -1.501F, 0.0F);
    }

    private void drawSkeleton(MatrixStack matrices, PlayerEntityModel model,
                              VertexConsumer lineBuffer, VertexConsumer glowBuffer,
                              int color, float pulse) {
        ModelPart root = model.getRootPart();

        Vec3d headTop = transformPt(matrices, root, model.head, 0, -8, 0);
        Vec3d headBase = transformPt(matrices, root, model.head, 0, 0, 0);
        Vec3d chestTop = transformPt(matrices, root, model.body, 0, 0, 0);
        Vec3d chestMid = transformPt(matrices, root, model.body, 0, 6, 0);
        Vec3d pelvis = transformPt(matrices, root, model.body, 0, 12, 0);
        Vec3d lShoulder = transformPt(matrices, root, model.body, 5, 2, 0);
        Vec3d rShoulder = transformPt(matrices, root, model.body, -5, 2, 0);
        Vec3d lHip = transformPt(matrices, root, model.body, 2, 12, 0);
        Vec3d rHip = transformPt(matrices, root, model.body, -2, 12, 0);
        Vec3d laTop = transformPt(matrices, root, model.leftArm, 0, 0, 0);
        Vec3d laMid = transformPt(matrices, root, model.leftArm, 0, 4, 0);
        Vec3d laBot = transformPt(matrices, root, model.leftArm, 0, 10, 0);
        Vec3d raTop = transformPt(matrices, root, model.rightArm, 0, 0, 0);
        Vec3d raMid = transformPt(matrices, root, model.rightArm, 0, 4, 0);
        Vec3d raBot = transformPt(matrices, root, model.rightArm, 0, 10, 0);
        Vec3d llTop = transformPt(matrices, root, model.leftLeg, 0, 0, 0);
        Vec3d llMid = transformPt(matrices, root, model.leftLeg, 0, 6, 0);
        Vec3d llBot = transformPt(matrices, root, model.leftLeg, 0, 12, 0);
        Vec3d rlTop = transformPt(matrices, root, model.rightLeg, 0, 0, 0);
        Vec3d rlMid = transformPt(matrices, root, model.rightLeg, 0, 6, 0);
        Vec3d rlBot = transformPt(matrices, root, model.rightLeg, 0, 12, 0);

        Vec3d[][] bones = {
            {headTop, headBase}, {headBase, chestTop}, {chestTop, chestMid}, {chestMid, pelvis},
            {lShoulder, rShoulder}, {lHip, rHip},
            {lShoulder, laTop}, {rShoulder, raTop}, {lHip, llTop}, {rHip, rlTop},
            {laTop, laMid}, {laMid, laBot}, {raTop, raMid}, {raMid, raBot},
            {llTop, llMid}, {llMid, llBot}, {rlTop, rlMid}, {rlMid, rlBot}
        };

        for (Vec3d[] bone : bones) {
            // Линия
            if (lineBuffer != null) {
                lineBuffer.vertex(IDENTITY_MATRIX, (float) bone[0].x, (float) bone[0].y, (float) bone[0].z).color(color);
                lineBuffer.vertex(IDENTITY_MATRIX, (float) bone[1].x, (float) bone[1].y, (float) bone[1].z).color(color);
            }
            // Свечение (bloom quad вдоль кости) — пульсирует
            if (glowBuffer != null) {
                float glowWidth = 0.025F + 0.025F * pulse; // от 0.025 до 0.05
                int glowAlpha = (int) (((color >> 24) & 0xFF) * pulse);
                int glowColor = (glowAlpha << 24) | (color & 0x00FFFFFF);
                this.drawGlowBone(glowBuffer, bone[0], bone[1], glowColor, glowWidth);
            }
        }
    }

    private void drawGlowBone(VertexConsumer buffer, Vec3d start, Vec3d end, int color, float width) {
        Vec3d mid = start.add(end).multiply(0.5);
        Vec3d boneDir = end.subtract(start);
        double boneLen = boneDir.length();
        if (boneLen < 0.001) return;
        boneDir = boneDir.normalize();

        // boneDir × up даёт горизонтальную нормаль. Если кость вертикальна — берём forward.
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = boneDir.crossProduct(up);
        if (right.lengthSquared() < 0.0001) {
            right = boneDir.crossProduct(new Vec3d(0, 0, 1));
        }
        right = right.normalize().multiply(width);

        // 4 вершины: start±right, end±right
        float x0 = (float) (start.x - right.x), y0 = (float) (start.y - right.y), z0 = (float) (start.z - right.z);
        float x1 = (float) (start.x + right.x), y1 = (float) (start.y + right.y), z1 = (float) (start.z + right.z);
        float x2 = (float) (end.x + right.x),   y2 = (float) (end.y + right.y),   z2 = (float) (end.z + right.z);
        float x3 = (float) (end.x - right.x),   y3 = (float) (end.y - right.y),   z3 = (float) (end.z - right.z);

        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;

        buffer.vertex(IDENTITY_MATRIX, x0, y0, z0).texture(0.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(IDENTITY_MATRIX, x1, y1, z1).texture(1.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(IDENTITY_MATRIX, x2, y2, z2).texture(1.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(IDENTITY_MATRIX, x3, y3, z3).texture(0.0F, 1.0F).color(r, g, b, a);
    }

    private Vec3d transformPt(MatrixStack baseStack, ModelPart root, ModelPart part, float x, float y, float z) {
        baseStack.push();
        applyPartTransform(baseStack, root);
        applyPartTransform(baseStack, part);
        Vector3f pos = baseStack.peek().getPositionMatrix()
                .transformPosition(x * MODEL_SCALE, y * MODEL_SCALE, z * MODEL_SCALE, new Vector3f());
        baseStack.pop();
        return new Vec3d(pos.x, pos.y, pos.z);
    }

    private void applyPartTransform(MatrixStack matrices, ModelPart part) {
        matrices.translate(part.originX * MODEL_SCALE, part.originY * MODEL_SCALE, part.originZ * MODEL_SCALE);
        if (part.roll != 0.0F || part.yaw != 0.0F || part.pitch != 0.0F) {
            matrices.multiply(new Quaternionf().rotateZYX(part.roll, part.yaw, part.pitch));
        }
        if (part.xScale != 1.0F || part.yScale != 1.0F || part.zScale != 1.0F) {
            matrices.scale(part.xScale, part.yScale, part.zScale);
        }
    }
}
