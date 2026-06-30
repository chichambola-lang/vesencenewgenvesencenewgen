package vesence.module.impl.visuals.elements.modern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import javax.validation.constraints.Min;
import java.awt.*;

@Environment(EnvType.CLIENT)
public class ModernWatermarkElement extends HudElement {

    private static final float RECT_H = 85;
    private final Animation2 widthAnim = new Animation2();

    public ModernWatermarkElement() {
        super("Watermark", 10f, 10f);
        this.enabled = false;
        widthAnim.set(200);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        String pingStr = (MinecraftClient.getInstance().getNetworkHandler() != null && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()) != null)
                ? String.valueOf(MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()).getLatency())
                : "Неизвестно";
        float targetW = renderer.measureText(FontRegistry.MONTSERRAT, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 29).width +
                renderer.measureText(FontRegistry.MONTSERRAT, "Ping: " + pingStr, 29).width + 133;
        widthAnim.update();
        widthAnim.run(targetW, 0.2, Easings.SINE_OUT, true);
        drawHudPanel(renderer,x, y, targetW, 43, 1);
        renderer.text(FontRegistry.LOGO, x + 1.5f, y + 35.5f, 85, "A  ", new int[] {Renderer2D.ColorUtil.getClientColor(),
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.6f)}, 5);
        renderer.rect(x + 43, y + 12, 1, 20, ColorUtil.replAlpha(-1, 25));
        renderer.text(FontRegistry.MONTSERRAT, x + 76, y + 28, 29, ColorFormat.color(-1) + "FPS: " + ColorFormat.reset() + MinecraftClient.getInstance().getCurrentFps(), new int[] {Renderer2D.ColorUtil.getClientColor(),
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.6f)}, 6);
        renderer.text(FontRegistry.MON, x + 52.5f, y + 29.5f, 32, "f ", new int[] {Renderer2D.ColorUtil.getClientColor(),
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.6f)}, 0);
        renderer.rect(x + 86 +
                renderer.measureText(FontRegistry.MONTSERRAT, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 29).width, y + 12, 1, 20, ColorUtil.replAlpha(-1, 25));
        renderer.text(FontRegistry.MONTSERRAT, x + 120 +
                renderer.measureText(FontRegistry.MONTSERRAT, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 29).width, y + 28, 29, ColorFormat.color(-1) + "Ping: " + ColorFormat.reset() + pingStr, new int[] {Renderer2D.ColorUtil.getClientColor(),
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.6f)}, 6);
        renderer.text(FontRegistry.MON, x + 97 +
                renderer.measureText(FontRegistry.MONTSERRAT, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 29).width, y + 29.5f, 32, "J ", new int[] {Renderer2D.ColorUtil.getClientColor(),
                ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.6f)}, 0);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        String pingStr = (MinecraftClient.getInstance().getNetworkHandler() != null && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()) != null)
                ? String.valueOf(MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()).getLatency())
                : "Неизвестно";
        float targetW = renderer.measureText(FontRegistry.MONTSERRAT, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 29).width +
                renderer.measureText(FontRegistry.MONTSERRAT, "Ping: " + pingStr, 29).width + 133;
        return targetW;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return 43;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        String pingStr = (MinecraftClient.getInstance().getNetworkHandler() != null && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()) != null)
                ? String.valueOf(MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()).getLatency())
                : "Неизвестно";
        float targetW = renderer.measureText(FontRegistry.MONTSERRAT, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 29).width +
                renderer.measureText(FontRegistry.MONTSERRAT, "Ping: " + pingStr, 29).width + 133;
        return targetW;
    }
}
