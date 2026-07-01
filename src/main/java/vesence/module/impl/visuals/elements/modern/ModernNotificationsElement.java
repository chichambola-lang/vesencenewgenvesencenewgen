package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.notifications.Notifications;
import vesence.utils.render.text.FontObject;

@Environment(EnvType.CLIENT)
public class ModernNotificationsElement extends HudElement {

    private static final float DEFAULT_X = 850f;
    private static final float DEFAULT_Y = 570f;

    public ModernNotificationsElement() {
        super("Notifications", DEFAULT_X, DEFAULT_Y);

        addSetting(Notifications.mentionAlert);
        addSetting(Notifications.potionAlert);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        float centerX = x + getWidth(renderer, font) / 2f;
        Notifications.render(renderer, ctx, centerX, y, 1f);
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        float w = Notifications.boundsWidth();
        return w > 1 ? w : 220f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        float h = Notifications.boundsHeight();
        return h > 1 ? h : 40f;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return getWidth(renderer, font);
    }
}
