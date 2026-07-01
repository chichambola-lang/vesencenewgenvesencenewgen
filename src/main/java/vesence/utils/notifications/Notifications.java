package vesence.utils.notifications;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;

import java.util.concurrent.CopyOnWriteArrayList;

@Environment(EnvType.CLIENT)
public class Notifications {

    private static final CopyOnWriteArrayList<Notification> active = new CopyOnWriteArrayList<>();
    private static final float FONT_SIZE = 27;
    private static final float PADDING_H = 12;
    private static final float PADDING_V = 7;
    private static final float NOTIF_GAP = -2;
    private static final float FADE_DURATION = 0.2f;
    private static final float Y_DURATION = 0.4f;
    private static final float TOGGLE_W = 25;
    private static final float TOGGLE_H = 14;
    private static final float TOGGLE_KNOB = 10;
    private static final float TOGGLE_GAP = 8;
    private static final float ICON_SIZE = 26.5f;
    private static final float ICON_GAP = 3;

    public static final BooleanSetting blurSetting = new BooleanSetting("Размытие", true);

    public static final BooleanSetting mentionAlert = new BooleanSetting("Упоминание в чате", true);
    public static final BooleanSetting potionAlert = new BooleanSetting("Окончание зелий", true);

    private static boolean nextFromLeft = true;

    public static void add(String text) {
        active.add(new Notification(text, (long) (1.5 * 1000)));
    }

    public static void add(String text, double lifetimeSeconds) {
        active.add(new Notification(text, (long) (lifetimeSeconds * 1000)));
    }

    public static void add(String text, boolean state) {
        active.add(new Notification(text, state, (long) (1.5 * 1000)));
    }

    public static void add(String text, boolean state, double lifetimeSeconds) {
        active.add(new Notification(text, state, (long) (lifetimeSeconds * 1000)));
    }

    public static void add(ItemStack stack, String text) {
        active.add(new Notification(text, stack, (long) (1.5 * 1000)));
    }

    public static void add(ItemStack stack, String text, double lifetimeSeconds) {
        active.add(new Notification(text, stack, (long) (lifetimeSeconds * 1000)));
    }

    public static void add(String iconText, String text) {
        active.add(new Notification(text, iconText, (long) (1.5 * 1000)));
    }

    public static void add(String iconText, String text, double lifetimeSeconds) {
        active.add(new Notification(text, iconText, (long) (lifetimeSeconds * 1000)));
    }

    public static void addMention() {
        add("A", ColorFormat.color(255,255,255) + "Вас упомянули в чате!", 3.0);
    }

    public static void addPotionEnd(String potionName) {
        add("A", ColorFormat.color(255,255,255) + "У вас кончилась " + potionName + "!", 3.0);
    }

    private static Notification example = null;

    public static void showExample() {
        if (example == null) {
            example = new Notification(ColorFormat.color(255, 255, 255) + "Пример уведомления", "A", Long.MAX_VALUE / 2);
        }
    }

    public static void hideExample() {
        example = null;
    }

    private static float lastCenterX, lastTopY, lastWidth, lastHeight;

    public static float boundsWidth()  { return lastWidth; }
    public static float boundsHeight() { return lastHeight; }

    public static void render(Renderer2D renderer, DrawContext ctx, int screenWidth, int screenHeight) {
        render(renderer, ctx, screenWidth / 2f, screenHeight / 2f + 30f, 1f);
    }

    public static void render(Renderer2D renderer, DrawContext ctx, float centerX, float topY, float scale) {
        long now = System.currentTimeMillis();

        active.removeIf(n -> {
            n.update(now);
            return n.isDead();
        });

        java.util.List<Notification> list = new java.util.ArrayList<>(active);
        if (example != null) {
            example.update(now);
            list.add(example);
        }

        if (list.isEmpty()) {
            lastWidth = 0;
            lastHeight = 0;
            lastCenterX = centerX;
            lastTopY = topY;
            return;
        }

        final float FS = FONT_SIZE * scale;
        final float padH = PADDING_H * scale;
        final float padV = PADDING_V * scale;
        final float gap = NOTIF_GAP * scale;
        final float togW = TOGGLE_W * scale, togH = TOGGLE_H * scale, togKnob = TOGGLE_KNOB * scale, togGap = TOGGLE_GAP * scale;
        final float icoSize = ICON_SIZE * scale, icoGap = ICON_GAP * scale;

        float[] heights = new float[list.size()];

        for (int i = 0; i < list.size(); i++) {
            Notification n = list.get(i);
            float textW = renderer.measureText(FontRegistry.MONTSERRAT, n.text, FS).width;
            heights[i] = FS + padV * 2;

            if (n.isToggle) {
                n.width = textW + togW + togGap + padH * 1.5f;
            } else if (hasItemIcon(n)) {
                n.width = textW + icoSize + icoGap + padH * 1.5f - 7 * scale;
            } else if (hasTextIcon(n)) {
                float iconW = renderer.measureText(FontRegistry.NOTIFY, n.iconText, FS).width;
                n.width = textW + iconW + icoGap + padH * 1.5f + 10 * scale;
            } else {
                n.width = textW + padH * 1.5f;
            }
        }

        float targetY = 0f;
        float maxW = 0;
        float totalH = 0;
        for (int i = 0; i < list.size(); i++) {
            Notification n = list.get(i);
            float h = heights[i];
            n.targetY = targetY;
            n.yAnim.update();

            if (!n.placed) {
                n.yAnim.set(targetY);
                n.placed = true;
            } else if (Math.abs(n.yAnim.get() - targetY) > 0.5f) {
                n.yAnim.run(targetY, Y_DURATION, Easings.BACK_OUT, true);
            }

            maxW = Math.max(maxW, n.width);
            totalH += h + gap;
            targetY += h + gap;
        }
        lastCenterX = centerX;
        lastTopY = topY;
        lastWidth = maxW;
        lastHeight = Math.max(0, totalH - gap);

        for (Notification n : list) {
            float alpha = (float) n.anim.get();
            if (alpha < 0.005f) continue;

            float w = n.width;
            float h = 34 * scale;
            float animY = (float) n.yAnim.get();
            float drawY = topY + animY;

            float slideDir = n.fromLeft ? -1f : 1f;
            float slideDist = 45f * scale;
            float xOffset = (1f - alpha) * slideDist * slideDir;
            float cx = centerX + xOffset;

            renderer.pushAlpha(alpha);
            HudElement.drawHudPanel(renderer, cx - w / 2f, drawY, w, h, alpha);
            renderer.popAlpha();

            float textY = drawY + (h + FS) / 2f - 8 * scale;
            int[] colors = {
                    ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (255 * alpha))
            };

            float textW = renderer.measureText(FontRegistry.MONTSERRAT, n.text, FS).width;

            if (n.isToggle) {
                float toggleX = cx - w / 2f + padH - 4 * scale;
                float toggleY = drawY + (h - togH) / 2f;

                if (n.toggleStart == -1) {
                    n.toggleStart = System.currentTimeMillis();
                }
                long elapsed = System.currentTimeMillis() - n.toggleStart;
                float raw = Math.min(elapsed / 800f, 1.0f);
                float eased = 1.0f - (float) Math.pow(1.0f - raw, 3.0f);

                float t = n.toggleState ? eased : (1.0f - eased);

                renderer.drawSquircle(toggleX, toggleY, togW, togH, 1,
                        BorderRadius.all(togH / 2f), ColorUtil.overCol(
                                ColorUtil.replAlpha(-1, (int) (20 * alpha)),
                                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (185 * alpha)),
                                t));
                renderer.drawSquircleOutline(toggleX, toggleY, togW, togH, 1,
                        BorderRadius.all(togH / 2f), ColorUtil.overCol(
                                ColorUtil.replAlpha(-1, (int) (35 * alpha)),
                                ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), (int) (215 * alpha)),
                                t), 1);

                float maxTravel = togW - togKnob - 7 * scale;
                float knobX = toggleX + 2 * scale + maxTravel * t;
                float knobY = toggleY + (togH - togKnob) / 2f;

                renderer.drawSquircle(knobX, knobY, togKnob + 3 * scale, togKnob, 1,
                        BorderRadius.all(togKnob / 2f), ColorUtil.overCol(
                                ColorUtil.replAlpha(-1, (int) (125 * alpha)),
                                ColorUtil.replAlpha(-1, (int) (255 * alpha)),
                                t));

                float textX = toggleX + togW + togGap + textW / 2f;
                renderer.textCenter(FontRegistry.MONTSERRAT, textX, textY, FS, n.text, colors, 5);

            } else if (hasTextIcon(n)) {
                float iconW = renderer.measureText(FontRegistry.NOTIFY, n.iconText, FS).width;
                float contentW = iconW + icoGap + textW;
                float startX = cx - contentW / 2f;

                float iconX = startX + iconW / 2f;
                float textX = startX + iconW + icoGap + textW / 2f;

                renderer.textCenter(FontRegistry.NOTIFY, iconX - 2 * scale, textY - 1 * scale, FS, n.iconText, colors, 5);
                renderer.textCenter(FontRegistry.MONTSERRAT, textX + 3 * scale, textY, FS, n.text, colors, 5);

            } else if (hasItemIcon(n)) {
                float contentW = icoSize + icoGap + textW;
                float startX = cx - contentW / 2f;

                float textX = startX + icoSize + icoGap + textW / 2f - 3 * scale;
                renderer.textCenter(FontRegistry.MONTSERRAT, textX, textY, FS, n.text, colors, 5);

                if (alpha > 0.01f) {
                    float iconX = startX;
                    float iconY = drawY + (h - icoSize) / 2f;

                    float guiScale = (float) vesence.utils.other.Mathf.getScaleFactor();
                    float baseScale = 0.80f * scale;
                    float iconScale = baseScale * alpha;
                    float pivotX = iconX + icoSize / 2f - 5 * scale;
                    float pivotY = iconY + icoSize / 2f;

                    org.joml.Matrix3x2fStack matrices = ctx.getMatrices();
                    matrices.pushMatrix();
                    matrices.translate(pivotX / guiScale, pivotY / guiScale);
                    matrices.scale(iconScale, iconScale);
                    matrices.translate(-(icoSize / 2f) / guiScale, -(icoSize / 2f) / guiScale - 1.5f);
                    ctx.drawItem(n.stack, 0, 0);
                    matrices.popMatrix();
                }
            } else {
                renderer.textCenter(FontRegistry.MONTSERRAT, cx, textY, FS, n.text, colors, 5);
            }
        }
    }

    public static void clear() {
        active.clear();
    }

    private static boolean hasItemIcon(Notification n) {
        return n.stack != null && !n.stack.isEmpty();
    }

    private static boolean hasTextIcon(Notification n) {
        return n.iconText != null && !n.iconText.isEmpty();
    }

    private static class Notification {
        final String text;
        final ItemStack stack;
        final String iconText;
        final long lifetimeMs;
        final long createdAt;
        final boolean isToggle;
        final boolean toggleState;
        final boolean fromLeft;
        final Animation2 anim = new Animation2();
        final Animation2 yAnim = new Animation2();
        long toggleStart;
        float targetY;
        float width;
        boolean placed;

        Notification(String text, long lifetimeMs) {
            this(text, null, null, false, false, lifetimeMs);
        }

        Notification(String text, ItemStack stack, long lifetimeMs) {
            this(text, stack, null, false, false, lifetimeMs);
        }

        Notification(String text, String iconText, long lifetimeMs) {
            this(text, null, iconText, false, false, lifetimeMs);
        }

        Notification(String text, boolean toggleState, long lifetimeMs) {
            this(text, null, null, true, toggleState, lifetimeMs);
        }

        private Notification(String text, ItemStack stack, String iconText, boolean isToggle, boolean toggleState, long lifetimeMs) {
            this.text = text;
            this.stack = stack;
            this.iconText = iconText;
            this.isToggle = isToggle;
            this.toggleState = toggleState;
            this.lifetimeMs = lifetimeMs;
            this.createdAt = System.currentTimeMillis();
            this.fromLeft = nextFromLeft;
            nextFromLeft = !nextFromLeft;
            this.toggleStart = -1;
            anim.set(0.0);
            yAnim.set(0.0);
            targetY = 0;
            placed = false;
        }

        void update(long now) {
            long elapsed = now - createdAt;
            anim.update();

            if (elapsed < FADE_DURATION * 1000) {
                anim.run(1.0, FADE_DURATION, Easings.SINE_OUT);
            } else if (elapsed > lifetimeMs) {
                anim.run(0.0, FADE_DURATION, Easings.SINE_OUT);
            }
        }

        boolean isDead() {
            long elapsed = System.currentTimeMillis() - createdAt;
            return elapsed > lifetimeMs + FADE_DURATION * 1000 && anim.get() < 0.005;
        }
    }
}
