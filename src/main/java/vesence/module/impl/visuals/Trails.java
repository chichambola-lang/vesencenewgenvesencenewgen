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
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@IModule(name = "Trails", description = "Оставляет плавный след за игроком", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Trails extends Module {

    public final SliderSetting lifetime = new SliderSetting("Время жизни", 500, 100, 2000, 50, false);
    public final BooleanSetting onlySelf = new BooleanSetting("Только свой", true);
    public final BooleanSetting firstPerson = new BooleanSetting("Вид от 1-го лица", false);

    private static final RenderPipeline QUADS_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                    .withLocation(Identifier.of("vesence", "pipeline/world/trails_quads"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());
    private static final RenderLayer QUADS_LAYER = RenderLayer.of("trails_quads",
            RenderSetup.builder(QUADS_PIPELINE).expectedBufferSize(262144).translucent().build());

    private final Map<Integer, List<Point>> entityPoints = new HashMap<>();
    private BufferAllocator allocator = null;
    private Immediate immediate = null;

    public Trails() {
        addSettings(lifetime, onlySelf, firstPerson);
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (!this.enable) return;
        if (mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        long lifeMs = (long) lifetime.current;
        float tickDelta = event.getTickDelta();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (onlySelf.get() && player != mc.player) continue;
            int id = player.getId();

            if (player == mc.player && mc.options.getPerspective().isFirstPerson() && !firstPerson.get()) {

                entityPoints.remove(id);
                continue;
            }
            List<Point> points = entityPoints.computeIfAbsent(id, k -> new ArrayList<>());

            points.removeIf(p -> now - p.time > lifeMs);

            while (points.size() > 500) points.remove(0);

            Vec3d pos = player.getLerpedPos(tickDelta);
            points.add(new Point(pos.x, pos.y, pos.z, now));
        }

        entityPoints.keySet().removeIf(id -> mc.world.getEntityById(id) == null);

        MatrixStack matrices = event.getMatrixStack();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        if (allocator == null) {
            allocator = new BufferAllocator(262144);
            immediate = VertexConsumerProvider.immediate(allocator);
        }

        try {
            VertexConsumer buffer = immediate.getBuffer(QUADS_LAYER);
            int clientColor = Renderer2D.ColorUtil.getClientColor();
            int r = (clientColor >> 16) & 0xFF;
            int g = (clientColor >> 8) & 0xFF;
            int b = clientColor & 0xFF;

            for (List<Point> points : entityPoints.values()) {
                int pointCount = points.size();
                if (pointCount < 2) continue;

                float entityHeight = 1.8f;

                for (int i = 0; i < pointCount - 1; i++) {
                    Point p1 = points.get(i);
                    Point p2 = points.get(i + 1);

                    float alpha1 = (i / (float) pointCount) * 0.5f;
                    float alpha2 = ((i + 1) / (float) pointCount) * 0.5f;
                    int a1 = (int) (alpha1 * 255);
                    int a2 = (int) (alpha2 * 255);

                    double x1 = p1.x - cameraPos.x;
                    double y1 = p1.y - cameraPos.y;
                    double z1 = p1.z - cameraPos.z;
                    double x2 = p2.x - cameraPos.x;
                    double y2 = p2.y - cameraPos.y;
                    double z2 = p2.z - cameraPos.z;

                    buffer.vertex(matrix, (float) x1, (float) y1, (float) z1).color(r, g, b, a1);
                    buffer.vertex(matrix, (float) x1, (float) (y1 + entityHeight), (float) z1).color(r, g, b, a1);
                    buffer.vertex(matrix, (float) x2, (float) (y2 + entityHeight), (float) z2).color(r, g, b, a2);
                    buffer.vertex(matrix, (float) x2, (float) y2, (float) z2).color(r, g, b, a2);
                }
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
        entityPoints.clear();
        if (allocator != null) {
            try {
                allocator.close();
            } catch (Exception ignored) {}
            allocator = null;
            immediate = null;
        }
    }

    private static class Point {
        final double x, y, z;
        final long time;

        Point(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }
}
