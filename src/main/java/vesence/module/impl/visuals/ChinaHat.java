package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.BufferAllocator;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;
import vesence.renderengine.render.Renderer2D;

@IModule(name = "China Hat", description = "Китайская шляпа над головой игрока", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ChinaHat extends Module {

    private static final RenderPipeline CONE_PIPELINE = RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                .withLocation(Identifier.of("vesence", "china_hat_cone"))
                .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.LIGHTNING)
                .build());
    private static final RenderPipeline CONE_LINE_PIPELINE = RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                .withLocation(Identifier.of("vesence", "china_hat_line"))
                .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINE_STRIP)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.LIGHTNING)
                .build());
    private static final RenderLayer CONE_LAYER = RenderLayer.of("china_hat_cone", RenderSetup.builder(CONE_PIPELINE).expectedBufferSize(2048).translucent().build());
    private static final RenderLayer CONE_LINE_LAYER = RenderLayer.of("china_hat_line", RenderSetup.builder(CONE_LINE_PIPELINE).expectedBufferSize(1024).translucent().build());

    private BufferAllocator allocator = null;
    private Immediate immediate = null;

    public ChinaHat() {
        super();
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.world == null || mc.player == null) return;

        float tickDelta = event.getTickDelta();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        MatrixStack matrices = event.getMatrixStack();

        if (allocator == null) {
            allocator = new BufferAllocator(262144);
            immediate = VertexConsumerProvider.immediate(allocator);
        }

        try {
            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player != mc.player && !FriendStorage.isFriend(player.getName().getString())) {
                    continue;
                }

                if (player == mc.player && mc.options.getPerspective().isFirstPerson()) {
                    continue;
                }

                matrices.push();

                Vec3d lerpedPos = player.getLerpedPos(tickDelta);
                double x = lerpedPos.x - cameraPos.x;
                double y = lerpedPos.y - cameraPos.y + player.getHeight();
                double z = lerpedPos.z - cameraPos.z;

                boolean hasHelmet = !player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty();

                double neckY = y - (player.isSneaking() ? 0.35 : 0.37);
                matrices.translate(x, neckY, z);

                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.getHeadYaw()));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.getPitch()));

                double localOffset = hasHelmet ? 0.43 : 0.37;
                matrices.translate(0.0, localOffset, 0.0);

                drawConeHat(matrices.peek().getPositionMatrix(), immediate, player);

                matrices.pop();
            }
            immediate.draw();
        } catch (Exception e) {

            if (allocator != null) {
                try {
                    allocator.close();
                } catch (Exception ignored) {}
            }
            allocator = null;
            immediate = null;
        }
    }

    @Override
    public void onDisable() {
        if (allocator != null) {
            try {
                allocator.close();
            } catch (Exception ignored) {}
            allocator = null;
            immediate = null;
        }
    }

    private void drawConeHat(Matrix4f matrix, Immediate immediate, PlayerEntity player) {
        VertexConsumer consumer = immediate.getBuffer(CONE_LAYER);

        float width = player.getWidth() + 0.1f;
        float apexHeight = 0.25f;
        int clientColor = Renderer2D.ColorUtil.getClientColor();

        float ar = ((clientColor >> 16) & 0xFF) / 255.0f;
        float ag = ((clientColor >> 8) & 0xFF) / 255.0f;
        float ab = (clientColor & 0xFF) / 255.0f;

        int slices = 40;

        for (int i = 0; i < slices; i++) {
            double angle1 = (i * 2.0 * Math.PI) / slices;
            double angle2 = ((i + 1) * 2.0 * Math.PI) / slices;

            int color1 = ColorUtil.fade(i * 10);
            int color2 = ColorUtil.fade((i + 1) * 10);

            float r1 = ((color1 >> 16) & 0xFF) / 255.0f;
            float g1 = ((color1 >> 8) & 0xFF) / 255.0f;
            float b1 = (color1 & 0xFF) / 255.0f;

            float r2 = ((color2 >> 16) & 0xFF) / 255.0f;
            float g2 = ((color2 >> 8) & 0xFF) / 255.0f;
            float b2 = (color2 & 0xFF) / 255.0f;

            consumer.vertex(matrix, 0.0f, apexHeight, 0.0f).color(ar, ag, ab, 0.6f);
            consumer.vertex(matrix, (float) Math.sin(angle1) * width, 0.0f, (float) Math.cos(angle1) * width).color(r1, g1, b1, 0.6f);
            consumer.vertex(matrix, (float) Math.sin(angle2) * width, 0.0f, (float) Math.cos(angle2) * width).color(r2, g2, b2, 0.6f);
        }

        VertexConsumer lineConsumer = immediate.getBuffer(CONE_LINE_LAYER);
        for (int i = 0; i <= slices; i++) {
            double angle = (i * 2.0 * Math.PI) / slices;
            int color = ColorUtil.fade(i * 10);
            float r = ((color >> 16) & 0xFF) / 255.0f;
            float g = ((color >> 8) & 0xFF) / 255.0f;
            float b = (color & 0xFF) / 255.0f;
            lineConsumer.vertex(matrix, (float) Math.sin(angle) * width, 0.0f, (float) Math.cos(angle) * width).color(r, g, b, 1.0f);
        }
    }
}
