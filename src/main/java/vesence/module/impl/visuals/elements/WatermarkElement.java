package vesence.module.impl.visuals.elements;
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
public class WatermarkElement extends HudElement {

    private static final float RECT_H = 85;
    private final Animation2 widthAnim = new Animation2();

    public WatermarkElement() {
        super("Watermark", 10f, 10f);
        this.enabled = false;
        widthAnim.set(200);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        float targetW = 156;
        widthAnim.update();
        widthAnim.run(targetW, 0.2, Easings.SINE_OUT, true);
        String pingStr = (MinecraftClient.getInstance().getNetworkHandler() != null && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()) != null)
                ? String.valueOf(MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()).getLatency())
                : "0";
        float rectW = (float) widthAnim.get();
        float rectW2 = (float) renderer.measureText(FontRegistry.SF_MEDIUM, "FPS: " + MinecraftClient.getInstance().getCurrentFps() +
                "Ping: " + pingStr, 32).width + 112;
        Identifier LOGO_TEXTURE = Identifier.of("vesence", "textures/vesence_logo.png");

        drawHudPanel(renderer,x + RECT_H + 9, y, rectW, RECT_H / 2f - 5, 1);
        drawHudPanel(renderer,x, y, RECT_H, RECT_H, 1);
        renderer.textCenter(FontRegistry.LOGO, x + RECT_H / 2f, y + 65, 145, "A", Renderer2D.ColorUtil.getClientColor());
        renderer.text(FontRegistry.LOGO, x + RECT_H + 10, y + 31, 73, "A", Renderer2D.ColorUtil.getClientColor());
        renderer.text(FontRegistry.SF_MEDIUM, x + RECT_H + 48, y + 26, 32, "Vesence Client", -1);

        drawHudPanel(renderer, x + RECT_H + rectW + 17, y, rectW2, RECT_H / 2f - 5, 1);
        renderer.text(FontRegistry.ICON, x + RECT_H + 11 + rectW + 14.5f, y + 30, 51.5f, "Z", Renderer2D.ColorUtil.getClientColor());
        renderer.text(FontRegistry.SF_MEDIUM, x + RECT_H + 48 + rectW + 11, y + 26, 32, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), -1);
        renderer.text(FontRegistry.ICON, x + RECT_H + 11 + rectW + 14.5f +
                renderer.measureText(FontRegistry.SF_MEDIUM, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 32).width + 59, y + 30, 45, "V", Renderer2D.ColorUtil.getClientColor());
        renderer.text(FontRegistry.SF_MEDIUM, x + RECT_H + 49 + rectW + 10 +
                renderer.measureText(FontRegistry.SF_MEDIUM, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 32).width + 55, y + 26, 32, "Ping: " + pingStr, -1);
        renderer.rect(x + rectW + 17 + RECT_H + renderer.measureText(FontRegistry.SF_MEDIUM, "FPS: " + MinecraftClient.getInstance().getCurrentFps(), 32).width + 53
                , y + 17, 5,5, 15, ColorUtil.getColor(255, 55));

        String username = System.getProperty("user.name", "Unknown");
        String serverInfo;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            serverInfo = client.getCurrentServerEntry().address;
        } else if (client.isInSingleplayer()) {
            serverInfo = "Одиночная игра";
        } else {
            serverInfo = "Нет сервера";
        }

        float userTextW = renderer.measureText(FontRegistry.SF_MEDIUM, username, 32).width;
        float serverTextW = renderer.measureText(FontRegistry.SF_MEDIUM, serverInfo, 32).width;
        float infoPanelW = userTextW + serverTextW + 112;
        float infoPanelH = RECT_H / 2f - 5;
        float infoPanelY = y + infoPanelH + 8;
        float infoPanelX = x + RECT_H + 9;

        drawHudPanel(renderer,infoPanelX, infoPanelY, infoPanelW, infoPanelH, 1);

        float curX = infoPanelX;
        renderer.text(FontRegistry.ICON, curX + 11, infoPanelY + 29.5f, 47, "F", Renderer2D.ColorUtil.getClientColor());
        renderer.text(FontRegistry.SF_MEDIUM, curX + 42, infoPanelY + 26, 32, username, -1);

        float dotX = curX + 45 + userTextW + 7;
        renderer.rect(dotX, infoPanelY + 17, 5, 5, 15, ColorUtil.getColor(255, 55));

        float serverIconX = dotX + 14;
        renderer.text(FontRegistry.ICON, serverIconX, infoPanelY + 30, 45, "L", Renderer2D.ColorUtil.getClientColor());
        renderer.text(FontRegistry.SF_MEDIUM, serverIconX + 31, infoPanelY + 26, 32, serverInfo, -1);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return 156 + RECT_H;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return RECT_H;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        String pingStr = (MinecraftClient.getInstance().getNetworkHandler() != null && MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()) != null)
                ? String.valueOf(MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getUuid()).getLatency())
                : "0";
        float rectW = (float) widthAnim.get();
        float rectW2 = (float) renderer.measureText(FontRegistry.SF_MEDIUM, "FPS: " + MinecraftClient.getInstance().getCurrentFps() +
                "Ping: " + pingStr, 32).width + 112;
        return rectW2 + RECT_H + 173;
    }
}
