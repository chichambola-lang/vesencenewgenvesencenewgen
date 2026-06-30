package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public abstract class HudElement {

    public String name;
    public float x;
    public float y;

    public final float defaultX;
    public final float defaultY;
    public boolean enabled;
    public final List<BooleanSetting> settings = new ArrayList<>();
    public final List<ModeSetting> modeSettings = new ArrayList<>();
    public final List<MultiBooleanSetting> multiBooleanSettings = new ArrayList<>();

    public final List<BooleanSetting> bottomSettings = new ArrayList<>();
    public final List<ModeSetting> bottomModeSettings = new ArrayList<>();
    public final List<MultiBooleanSetting> bottomMultiBooleanSettings = new ArrayList<>();
    public boolean settingsOpen = false;

    public final BooleanSetting blurSetting = new BooleanSetting("Размытие", true);
    public static final BooleanSetting outline = new BooleanSetting("Контур", true);
    public final SliderSetting scaleSetting = new SliderSetting("Размер", 1.0, 0.5, 2.0, 0.1);

    public static boolean isBlurEnabled() {
        return vesence.module.impl.misc.ClickGui.blurHud.get();
    }

    public static void drawHudPanel(Renderer2D renderer, float x, float y, float w, float h, float alpha) {
        vesence.module.impl.misc.ClickGui.tickLimits();
        float globalAlpha = alpha;
        float corner = vesence.module.impl.misc.ClickGui.getHudCorner();
        boolean squircle = vesence.module.impl.misc.ClickGui.isHudSquircle();
        float squirt = vesence.module.impl.misc.ClickGui.getHudSquirt();
        int bgColor = ColorUtil.multAlpha(ColorUtil.getColor(0,0,0), ClickGui.hudAlpha.get().floatValue() * alpha);

        if (squircle) {
            if (isBlurEnabled()) {
                renderer.blurSquircle(x, y, w, h, vesence.module.impl.misc.ClickGui.blurStrengthHud.get().intValue(), squirt, vesence.utils.render.BorderRadius.all(corner), alpha);
            }
            renderer.drawSquircleGradient(x, y, w, h, squirt, vesence.utils.render.BorderRadius.all(corner), ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.08f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.08f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.1f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.1f), ClickGui.guiAlpha.get().floatValue() * alpha));
            renderContourSquircleOutline(renderer, x, y, w, h, squirt, vesence.utils.render.BorderRadius.all(corner), globalAlpha);
        } else {
            if (isBlurEnabled()) {
                renderer.blur(x, y, w, h, vesence.module.impl.misc.ClickGui.blurStrengthHud.get().intValue(), corner, alpha);
            }
            renderer.gradient(x, y, w, h, corner, ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.08f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.08f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.1f), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.1f), ClickGui.guiAlpha.get().floatValue() * alpha));
            renderer.gradientOutline(x, y, w, h, corner, ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 1), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 1), ClickGui.guiAlpha.get().floatValue() * alpha),
                    ColorUtil.multAlpha(ColorUtil.BLACK, 0),
                    ColorUtil.multAlpha(ColorUtil.BLACK, 0), 1.5f, true);
        }
    }

    public static void drawSteppedRect(Renderer2D renderer, float rightX, float topY,
                                        float[] widths, float[] rowHeights, int color,
                                        float corner, float stepRound) {
        if (widths == null || widths.length == 0) return;

        float curY = topY;
        for (int i = 0; i < widths.length; i++) {
            float w = widths[i];
            float h = rowHeights[i];
            if (w < 0.5f || h < 0.5f) { curY += h; continue; }

            float rowX = rightX - w;

            boolean isFirst = (i == 0);
            boolean isLast = (i == widths.length - 1);
            boolean widerThanPrev = !isFirst && widths[i] > widths[i - 1] + 0.5f;
            boolean widerThanNext = !isLast && widths[i] > widths[i + 1] + 0.5f;

            float rTL = isFirst ? corner : (widerThanPrev ? stepRound : 0);
            float rTR = isFirst ? corner : 0;
            float rBL = isLast ? corner : (widerThanNext ? stepRound : 0);
            float rBR = isLast ? corner : 0;

            renderer.rect(rowX, curY, w, h, rTL, rTR, rBR, rBL, color);
            curY += h;
        }
    }

    public final Animation2 alphaAnim = new Animation2();
    public final Animation2 scaleAnim = new Animation2();

    private boolean dragging = false;
    private float screenDragOffsetX = 0;
    private float screenDragOffsetY = 0;

    protected void setDragging(boolean v) {
        this.dragging = v;
    }

    public float targetX;
    public float targetY;
    public boolean targetInitialized = false;
    private long lastMoveTime = 0;

    public float jellyScaleX = 1f;
    public float jellyScaleY = 1f;
    public float jellyRotation = 0f;
    private float velX = 0f;
    private float velY = 0f;

    private final Animation2 hoverAnim = new Animation2();
    private final Animation2 pressAnim = new Animation2();

    private static final float OUTLINE_GAP   = 4f;
    private static final float LERP_SPEED = 18f;

    private static final float DRAG_LERP_SPEED = 17f;
    private static final float JELLY_LERP_SPEED = 13f;

    private static final float JELLY_STRETCH = 0.0011f;
    private static final float JELLY_MAX_STRETCH = 0.34f;
    private static final float JELLY_TILT = 0.013f;
    private static final float JELLY_MAX_TILT = 14f;
    private static final float GRID_SNAP = 10f;

    public HudElement(String name, float defaultX, float defaultY) {
        this.name     = name;
        this.x        = defaultX;
        this.y        = defaultY;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.targetX  = defaultX;
        this.targetY  = defaultY;
        this.enabled  = true;
        hoverAnim.set(0.0);
        pressAnim.set(0.0);
        alphaAnim.set(1.0);
        scaleAnim.set(1.0);
    }

    public float getScale() {
        return (float) scaleAnim.get();
    }

    public void addSetting(BooleanSetting setting) {
        settings.add(setting);
    }

    public void addModeSetting(ModeSetting setting) {
        modeSettings.add(setting);
    }

    public void addMultiBooleanSetting(MultiBooleanSetting setting) {
        multiBooleanSettings.add(setting);
    }

    public BooleanSetting getSetting(String settingName) {
        for (BooleanSetting s : settings) {
            if (s.name.equals(settingName)) return s;
        }
        return null;
    }

    public abstract void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext drawContext);
    public abstract float getWidth(Renderer2D renderer, FontObject font);
    public abstract float getHeight(Renderer2D renderer, FontObject font);

    protected void renderBackground(Renderer2D renderer, float x, float y, float w, float h, float rounding) {
        renderBackground(renderer, x, y, w, h, rounding, 1f);
    }

    protected void renderBackground(Renderer2D renderer, float x, float y, float w, float h, float rounding, float alpha) {
        renderer.rect(x, y, w, h, rounding, ColorUtil.getColor(25,alpha));
        if (isContourEnabled()) {
            renderer.rectOutline(x, y, w, h, rounding, getContourColor(alpha), 1f);
        } else {
            renderer.rectOutline(x, y, w, h, rounding, ColorUtil.getColor(255, (int) (25 * alpha)), 1);
        }
    }

    public static boolean isContourEnabled() {

        return false;
    }

    public static int getContourColor(float alpha) {
        return ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (90 * alpha));
    }

    public static int getGlobalContourColor(float alpha) {
        return getContourColor(alpha);
    }

    public static void renderContourRectOutline(Renderer2D renderer, float px, float py, float w, float h, float rounding, float alpha) {
        if (!isContourEnabled()) return;
        renderer.rectOutline(px, py, w, h, rounding, getContourColor(alpha), 1f);
    }

    public static void renderContourSquircleOutline(Renderer2D renderer, float px, float py, float w, float h,
                                                     float squirt, BorderRadius radius, float alpha) {
        if (!isContourEnabled()) return;
        renderer.drawSquircleOutline(px, py, w, h, squirt, radius, getContourColor(alpha), 1f);
    }

    public void renderInteraction(Renderer2D renderer, FontObject font,
                                  double mouseX, double mouseY, boolean isPressed) {
        hoverAnim.update();
        pressAnim.update();

        boolean hovered = isHovered(mouseX, mouseY, renderer, font);
        hoverAnim.run(hovered ? 1.0 : 0.0, 0.20, Easings.CUBIC_OUT);
        pressAnim.run(isPressed ? 1.0 : 0.0, 0.18, Easings.CUBIC_OUT);

        float hoverT = hoverAnim.get();

        if (hoverT < 0.005f) return;

        float w = getEffectiveWidth(renderer, font) - 30;
        float h = getHeight(renderer, font);

        float lineW = w * hoverT;
        float lineH = 2.5f;
        float lineX = x + (w - lineW) / 2f + 15;
        float lineY = y + h + 7;

        int alpha = (int) (200 * hoverT);
        int lineColor = Renderer2D.ColorUtil.swapAlpha(-1, alpha);
    }

    public boolean isHovered(double mouseX, double mouseY, Renderer2D renderer, FontObject font) {
        float s = getScale();
        float w = getEffectiveWidth(renderer, font) * s;
        float h = getHeight(renderer, font) * s;
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    public void onMousePress(double mouseX, double mouseY, Renderer2D renderer, FontObject font) {
        if (isHovered(mouseX, mouseY, renderer, font)) {
            dragging          = true;
            screenDragOffsetX = (float)(mouseX - x);
            screenDragOffsetY = (float)(mouseY - y);
            if (!targetInitialized) {
                targetX = x;
                targetY = y;
                targetInitialized = true;
            }
        }
    }

    public void onMouseRelease() {
        boolean wasDragging = dragging;
        dragging = false;

        if (wasDragging && vesence.Vesence.get != null && vesence.Vesence.get.configManager != null) {
            vesence.Vesence.get.configManager.autoSave();
        }
    }

    public void onMouseMove(double mouseX, double mouseY, Renderer2D renderer, FontObject font,
                            int screenWidth, int screenHeight) {
        if (!targetInitialized) {
            targetX = x;
            targetY = y;
            targetInitialized = true;
            lastMoveTime = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis();
        float dt = Math.min((now - lastMoveTime) / 1000f, 0.1f);
        lastMoveTime = now;

        if (dragging) {
            float s = getScale();
            float w = getWidth(renderer, font) * s;
            float h = getHeight(renderer, font) * s;

            float newX = (float)(mouseX - screenDragOffsetX);
            float newY = (float)(mouseY - screenDragOffsetY);

            if (GRID_SNAP > 0f) {
                newX = Math.round(newX / GRID_SNAP) * GRID_SNAP;
                newY = Math.round(newY / GRID_SNAP) * GRID_SNAP;
            }

            newX = Math.max(0, Math.min(newX, screenWidth  - w));
            newY = Math.max(0, Math.min(newY, screenHeight - h));

            targetX = newX;
            targetY = newY;

            float dragFactor = 1f - (float) Math.exp(-DRAG_LERP_SPEED * dt);
            float prevX = x, prevY = y;
            x += (targetX - x) * dragFactor;
            y += (targetY - y) * dragFactor;

            updateJelly(x - prevX, y - prevY, dt);
        } else {
            float factor = 1f - (float) Math.exp(-LERP_SPEED * dt);
            float prevX = x, prevY = y;
            x += (targetX - x) * factor;
            y += (targetY - y) * factor;
            if (Math.abs(x - targetX) < 0.4f) x = targetX;
            if (Math.abs(y - targetY) < 0.4f) y = targetY;

            updateJelly(x - prevX, y - prevY, dt);
        }
    }

    private void updateJelly(float dx, float dy, float dt) {
        if (dt <= 0f) return;

        float instVelX = dx / dt;
        float instVelY = dy / dt;
        float velSmooth = 1f - (float) Math.exp(-18f * dt);
        velX += (instVelX - velX) * velSmooth;
        velY += (instVelY - velY) * velSmooth;

        float speedX = Math.abs(velX);
        float speedY = Math.abs(velY);

        float stretchX = Math.min(JELLY_MAX_STRETCH, speedX * JELLY_STRETCH);
        float stretchY = Math.min(JELLY_MAX_STRETCH, speedY * JELLY_STRETCH);

        float targetScaleX = 1f + stretchX - stretchY * 0.6f;
        float targetScaleY = 1f + stretchY - stretchX * 0.6f;

        float targetTilt = Math.max(-JELLY_MAX_TILT, Math.min(JELLY_MAX_TILT, -velX * JELLY_TILT));

        float relax = 1f - (float) Math.exp(-JELLY_LERP_SPEED * dt);
        jellyScaleX += (targetScaleX - jellyScaleX) * relax;
        jellyScaleY += (targetScaleY - jellyScaleY) * relax;
        jellyRotation += (targetTilt - jellyRotation) * relax;
    }

    public boolean isDragging() {
        return dragging;
    }

    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return getWidth(renderer, font);
    }
}
